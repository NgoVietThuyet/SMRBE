# face-service: FastAPI + DeepFace (ArcFace). Image khá nặng (TensorFlow); chạy 1 worker để tiết kiệm RAM.
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    TF_CPP_MIN_LOG_LEVEL=2 \
    DEEPFACE_HOME=/models \
    TZ=Asia/Ho_Chi_Minh

RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt

# Tải sẵn trọng số lúc build để chạy offline; lỗi tải thì để lần chạy đầu tải (volume /models giữ lại cho lần sau).
RUN python -c "from deepface import DeepFace; DeepFace.build_model('ArcFace')" || true

COPY app.py .

RUN useradd -r -u 10001 face && mkdir -p /models && chown -R face /models /app
USER face

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=10s --start-period=300s --retries=5 \
    CMD curl -fsS http://localhost:8000/health || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
