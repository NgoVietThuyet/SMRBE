package vn.com.d2s.smr.dto.cf.file;

public record FileDownloadResponse(
        String url,
        int expiresIn
) {}
