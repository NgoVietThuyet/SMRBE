# syntax=docker/dockerfile:1
# =============================================================================
# SMR BE-Java (Spring Boot 3.5, Java 21)
#
#   docker build -t smr-backend ./BE-Java
#   docker run -d --name smr-backend -p 5228:5228 \
#     -e DB_URL="jdbc:sqlserver://<host>:<port>;databaseName=KLTNSMR;encrypt=true;trustServerCertificate=true" \
#     -e DB_USERNAME=... -e DB_PASSWORD=... \
#     -e JWT_KEY=<chuỗi bí mật ≥ 32 ký tự> \
#     -e MINIO_ENDPOINT=... -e MINIO_ACCESS_KEY=... -e MINIO_SECRET_KEY=... -e MINIO_BUCKET=kltn \
#     -e CORS_ALLOWED_ORIGINS=https://<domain FE> \
#     smr-backend
#
# Mọi giá trị bí mật truyền bằng biến môi trường lúc chạy, KHÔNG đưa vào image.
# =============================================================================

# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Tải thư viện trước (tận dụng cache khi chỉ sửa mã nguồn).
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/smr-backend-*.jar /src/app.jar

# ---------- Runtime ----------
FROM eclipse-temurin:21-jre-jammy

# fontconfig + phông DejaVu: Apache POI / AWT cần phông khi dựng XLSX (autoSizeColumn), ảnh, PDF có tiếng Việt.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system smr && useradd --system --gid smr --home /app smr

WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
RUN mkdir -p /app/uploads && chown -R smr:smr /app

USER smr

ENV SERVER_PORT=5228 \
    TZ=Asia/Ho_Chi_Minh \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true -Dfile.encoding=UTF-8"

EXPOSE 5228

# Bất kỳ phản hồi HTTP nào (kể cả 401) = ứng dụng đang chạy.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -s -o /dev/null http://localhost:${SERVER_PORT}/api/settings/public || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
