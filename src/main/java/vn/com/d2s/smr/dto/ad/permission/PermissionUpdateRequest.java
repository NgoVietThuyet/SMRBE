package vn.com.d2s.smr.dto.ad.permission;

import java.util.Map;

public record PermissionUpdateRequest(
        int version,
        Map<String, PermissionEffect> permissions
) {
}
