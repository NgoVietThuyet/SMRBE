package vn.com.d2s.smr.service.cf;

import java.io.InputStream;

public interface ObjectStorageService {
    void upload(String objectName, InputStream inputStream, long size, String contentType);
    String getDownloadUrl(String objectName, int expirySeconds);
    void delete(String objectName);
    String getBucketName();
}
