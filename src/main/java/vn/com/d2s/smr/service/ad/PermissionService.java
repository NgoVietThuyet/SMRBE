package vn.com.d2s.smr.service.ad;

import vn.com.d2s.smr.dto.ad.permission.EffectivePermissionResponse;
import vn.com.d2s.smr.dto.ad.permission.PermissionDocument;

import java.util.List;

public interface PermissionService {

    boolean hasPermission(String userName, String code);

    List<EffectivePermissionResponse> getEffectivePermissions(String userName);

    PermissionDocument parseAndValidate(String json);

    String serializeAndValidate(PermissionDocument document);
}
