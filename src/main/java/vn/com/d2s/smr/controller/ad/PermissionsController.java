package vn.com.d2s.smr.controller.ad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.dto.ad.permission.EffectivePermissionResponse;
import vn.com.d2s.smr.dto.ad.permission.PermissionCatalogGroupResponse;
import vn.com.d2s.smr.service.ad.PermissionCatalog;
import vn.com.d2s.smr.service.ad.PermissionService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/permissions")
public class PermissionsController {

    private final PermissionService permissionService;

    public PermissionsController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/catalog")
    public List<PermissionCatalogGroupResponse> catalog() {
        return PermissionCatalog.grouped();
    }

    @GetMapping("/me")
    public List<EffectivePermissionResponse> me(Principal principal) {
        return permissionService.getEffectivePermissions(principal.getName());
    }
}
