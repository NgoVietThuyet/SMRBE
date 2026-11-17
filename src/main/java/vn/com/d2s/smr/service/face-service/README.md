# face-service

Dịch vụ phụ nhận dạng khuôn mặt (FastAPI + DeepFace, mô hình ArcFace). Chỉ tính đặc trưng, không lưu gì.
Thư mục này nằm trong BE-Java (`src/main/java/vn/com/d2s/smr/service/face-service`) nên deploy cùng kho BE-Java; Maven bỏ qua
file Python, image BE không chứa nó (`.dockerignore`).

## Bật tính năng trên server
Trên server thường chỉ có `~/smr/compose.yaml`, `~/smr/.env`, `~/smr/BE-Java`, `~/smr/3.FrontEnd`.

1. `git pull` kho BE-Java (có thư mục này) và chạy `BE-Java/db/009_face.sql` trên SQL Server (nếu chưa).
2. Thêm khóa vào `.env` (không đưa lên git), chạy **một lần**:
   `printf 'FACE_ENABLED=true\nFACE_API_KEY=%s\nFACE_ENC_KEY=%s\n' "$(openssl rand -hex 32)" "$(openssl rand -base64 32)" >> .env`
   Mất `FACE_ENC_KEY` thì các mẫu khuôn mặt đã lưu không giải mã được (người dùng phải đăng ký lại).
3. Dựng lại, chọn một trong hai cách:
   - Nếu `compose.yaml` trên server đã là bản mới (có service `smr-face`):
     `docker compose up -d --build`
   - Nếu `compose.yaml` trên server còn là bản cũ, dùng thêm file ghép trong thư mục này (không cần sửa compose cũ):
     `docker compose -f compose.yaml -f BE-Java/src/main/java/vn/com/d2s/smr/service/face-service/compose.face.yaml up -d --build`
     Luôn dùng đủ hai `-f` cho mọi lệnh `docker compose` sau đó (ps, logs, down...).
4. Lần đầu `smr-face` tải mô hình nên mất vài phút; `docker compose ps` thấy `healthy` là xong. Cần thêm khoảng 3 GB RAM.

## API
- `GET /health`: 200 khi mô hình đã nạp xong, 503 khi đang khởi động.
- `POST /represent` (header `X-Api-Key`): `{"image": "<base64 JPEG>"}` →
  `{faces, embedding[512], isReal, antispoofScore, faceConfidence, quality, model, dim}`
