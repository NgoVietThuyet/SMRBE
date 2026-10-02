package vn.com.d2s.smr.dto.ad.permission;

import java.util.LinkedHashMap;
import java.util.Map;

public record PermissionDocument(int version, Map<String, PermissionEffect> permissions) {
    public PermissionDocument {
        permissions = permissions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(permissions);
    }

    public static PermissionDocument empty() {
        return new PermissionDocument(1, Map.of());
    }
}
