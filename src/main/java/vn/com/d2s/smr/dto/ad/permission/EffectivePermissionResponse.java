package vn.com.d2s.smr.dto.ad.permission;

public record EffectivePermissionResponse(
        String code,
        String name,
        boolean allowed,
        String effect,
        String sourceType,
        String sourceId,
        String sourceName
) {
}
