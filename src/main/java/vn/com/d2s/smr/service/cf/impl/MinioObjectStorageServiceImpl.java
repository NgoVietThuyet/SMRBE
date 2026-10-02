package vn.com.d2s.smr.service.cf.impl;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import vn.com.d2s.smr.service.cf.ObjectStorageService;

import java.io.InputStream;

@Service
@Primary
public class MinioObjectStorageServiceImpl implements ObjectStorageService {

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioObjectStorageServiceImpl(
            @Value("${smr.minio.endpoint:minio.d2s.vn}") String endpoint,
            @Value("${smr.minio.port:443}") int port,
            @Value("${smr.minio.access-key:admin}") String accessKey,
            @Value("${smr.minio.secret-key:admin123}") String secretKey,
            @Value("${smr.minio.use-ssl:true}") boolean useSsl,
            @Value("${smr.minio.bucket-name:KLTN}") String bucketName
    ) {
        this.bucketName = bucketName;
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint, port, useSsl)
                .credentials(accessKey, secretKey);
        this.minioClient = builder.build();
    }

    private void ensureBucket() throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    @Override
    public void upload(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Không thể lưu file lên MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDownloadUrl(String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo presigned URL từ MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            // Ignore error on delete compensation
        }
    }

    @Override
    public String getBucketName() {
        return bucketName;
    }
}
