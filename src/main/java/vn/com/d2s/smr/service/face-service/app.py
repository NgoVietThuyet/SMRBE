"""
face-service — dịch vụ phụ nhận dạng khuôn mặt cho SMR (FastAPI + DeepFace).

Chỉ tính đặc trưng khuôn mặt (embedding) + điểm "mặt thật" + chất lượng ảnh. KHÔNG lưu ảnh hay đặc trưng.
BE Spring Boot là nơi quyết định (so khớp, ngưỡng, cấp token). Chỉ mở trong mạng Docker nội bộ, gọi bằng X-Api-Key.
"""
import base64
import hmac
import logging
import os
import threading
from typing import Optional

import cv2
import numpy as np
from deepface import DeepFace
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

API_KEY = os.environ.get("FACE_API_KEY", "")
MODEL = os.environ.get("FACE_MODEL", "ArcFace")
DETECTOR = os.environ.get("FACE_DETECTOR", "opencv")  # opencv nhanh; retinaface chính xác hơn nhưng chậm hơn
MAX_BYTES = int(os.environ.get("FACE_MAX_IMAGE_BYTES", str(4 * 1024 * 1024)))
MAX_SIDE = int(os.environ.get("FACE_MAX_SIDE", "640"))

log = logging.getLogger("face-service")
app = FastAPI(title="smr-face-service")
_ready = threading.Event()


def _warmup() -> None:
    """Nạp (và tải nếu chưa có) mô hình nhận dạng + chống giả mạo ngay lúc khởi động, để lần quét mặt đầu tiên không bị chậm
    quá timeout của BE. /health chỉ trả 200 khi xong nên Docker healthcheck phản ánh đúng trạng thái sẵn sàng."""
    try:
        DeepFace.build_model(MODEL)
    except Exception as e:  # noqa: BLE001
        log.warning("Nạp mô hình %s lỗi: %s", MODEL, e)
    try:
        noise = np.random.randint(0, 255, (224, 224, 3), dtype=np.uint8)
        DeepFace.represent(img_path=noise, model_name=MODEL, detector_backend=DETECTOR,
                           enforce_detection=False, align=False, anti_spoofing=True)
    except Exception as e:  # noqa: BLE001 - ảnh nhiễu có thể bị chặn là giả mạo; mục đích chỉ là nạp trọng số
        log.info("Làm nóng xong (bỏ qua: %s)", e)
    _ready.set()
    log.info("face-service sẵn sàng (model=%s, detector=%s)", MODEL, DETECTOR)


@app.on_event("startup")
def _on_startup() -> None:
    threading.Thread(target=_warmup, name="warmup", daemon=True).start()


class RepresentRequest(BaseModel):
    image: str  # JPEG/PNG dạng base64 (có hoặc không có tiền tố data:image/...;base64,)


def _check_key(key: Optional[str]) -> None:
    if not API_KEY or not key or not hmac.compare_digest(key, API_KEY):
        raise HTTPException(status_code=401, detail="unauthorized")


def _decode(b64: str) -> np.ndarray:
    if "," in b64[:64]:
        b64 = b64.split(",", 1)[1]
    try:
        raw = base64.b64decode(b64, validate=False)
    except Exception:
        raise HTTPException(status_code=400, detail="bad_image")
    if not raw or len(raw) > MAX_BYTES:
        raise HTTPException(status_code=400, detail="bad_image")
    img = cv2.imdecode(np.frombuffer(raw, np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise HTTPException(status_code=400, detail="bad_image")
    h, w = img.shape[:2]
    scale = MAX_SIDE / max(h, w)
    if scale < 1:
        img = cv2.resize(img, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
    return img


def _quality(img: np.ndarray, area: dict) -> float:
    """0..1: mặt đủ lớn trong khung và đủ nét."""
    h, w = img.shape[:2]
    fw, fh = max(int(area.get("w", 0)), 1), max(int(area.get("h", 0)), 1)
    ratio = min(1.0, (fw * fh) / (h * w * 0.12))  # mặt chiếm ≥ ~12% khung là đạt
    x, y = max(int(area.get("x", 0)), 0), max(int(area.get("y", 0)), 0)
    crop = cv2.cvtColor(img[y:y + fh, x:x + fw], cv2.COLOR_BGR2GRAY) if fw > 1 and fh > 1 else None
    sharp = 0.0 if crop is None or crop.size == 0 else min(1.0, float(cv2.Laplacian(crop, cv2.CV_64F).var()) / 100.0)
    return round(min(ratio, sharp), 3)


@app.get("/health")
def health():
    if not _ready.is_set():
        raise HTTPException(status_code=503, detail="warming_up")
    return {"status": "ok", "model": MODEL, "detector": DETECTOR}


@app.post("/represent")
def represent(req: RepresentRequest, x_api_key: Optional[str] = Header(default=None)):
    _check_key(x_api_key)
    img = _decode(req.image)
    try:
        try:
            faces = DeepFace.represent(
                img_path=img, model_name=MODEL, detector_backend=DETECTOR,
                enforce_detection=True, align=True, anti_spoofing=True,
            )
            antispoof = True
        except TypeError:
            # Bản DeepFace cũ chưa có anti_spoofing: BE sẽ coi là không có điểm "mặt thật" và từ chối (fail-closed).
            faces = DeepFace.represent(
                img_path=img, model_name=MODEL, detector_backend=DETECTOR, enforce_detection=True, align=True,
            )
            antispoof = False
    except ValueError as e:
        msg = str(e).lower()
        if "spoof" in msg:
            return {"faces": 1, "embedding": None, "isReal": False, "antispoofScore": 0.0,
                    "faceConfidence": 0.0, "quality": 0.0}
        return {"faces": 0, "embedding": None, "isReal": None, "antispoofScore": None,
                "faceConfidence": 0.0, "quality": 0.0}

    if len(faces) != 1:
        return {"faces": len(faces), "embedding": None, "isReal": None, "antispoofScore": None,
                "faceConfidence": 0.0, "quality": 0.0}

    f = faces[0]
    return {
        "faces": 1,
        "embedding": [float(v) for v in f["embedding"]],
        "isReal": bool(f.get("is_real")) if antispoof and "is_real" in f else None,
        "antispoofScore": float(f["antispoof_score"]) if antispoof and "antispoof_score" in f else None,
        "faceConfidence": float(f.get("face_confidence") or 0.0),
        "quality": _quality(img, f.get("facial_area", {})),
        "model": MODEL,
        "dim": len(f["embedding"]),
    }
