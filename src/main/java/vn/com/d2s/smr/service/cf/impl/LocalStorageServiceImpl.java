package vn.com.d2s.smr.service.cf.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.d2s.smr.service.cf.ObjectStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class LocalStorageServiceImpl implements ObjectStorageService {

    private final Path rootLocation;
    private final String bucketName;

    public LocalStorageServiceImpl(@Value("${smr.storage.location:uploads}") String storageLocation,
                                   @Value("${smr.minio.bucket-name:KLTN}") String bucketName) {
        this.rootLocation = Paths.get(storageLocation);
        this.bucketName = bucketName;
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            // Ignore directory creation failure on init if already exists or unwritable
        }
    }

    @Override
    public void upload(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            Path targetPath = this.rootLocation.resolve(objectName);
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu file lên storage: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDownloadUrl(String objectName, int expirySeconds) {
        return "/storage/" + bucketName + "/" + objectName;
    }

    @Override
    public void delete(String objectName) {
        try {
            Path targetPath = this.rootLocation.resolve(objectName);
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            // Ignore delete error
        }
    }

    @Override
    public String getBucketName() {
        return bucketName;
    }
}
