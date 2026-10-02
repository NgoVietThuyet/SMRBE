package vn.com.d2s.smr.dto.ad.permission;

import java.util.List;

public record PermissionCatalogGroupResponse(
        String groupName,
        List<PermissionDefinition> permissions
) {
}
