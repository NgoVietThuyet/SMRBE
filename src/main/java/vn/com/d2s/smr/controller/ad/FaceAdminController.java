package vn.com.d2s.smr.controller.ad;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.config.audit.Audited;
import vn.com.d2s.smr.dto.ad.face.FaceActionResult;
import vn.com.d2s.smr.dto.ad.face.FaceAdminEnrollRequest;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.service.ad.FaceAuthService;

/**
 * Quản trị viên (có quyền menu Tài khoản — SecurityConfig gác POST/DELETE /api/human-resources/employees/**) đăng ký hoặc
 * xóa khuôn mặt của một tài khoản, dùng ở bước tạo tài khoản.
 */
@RestController
@RequestMapping("/api/human-resources/employees")
public class FaceAdminController {

    private final FaceAuthService faceAuthService;

    public FaceAdminController(FaceAuthService faceAuthService) {
        this.faceAuthService = faceAuthService;
    }

    @Audited(category = "ACCOUNT", action = "FACE_ENROLL", targetType = "ACCOUNT", targetPathVariable = "userName")
    @PostMapping("/{userName}/face")
    public ResponseEntity<ApiResponse<Void>> enroll(@PathVariable String userName, @Valid @RequestBody FaceAdminEnrollRequest request) {
        return result(faceAuthService.enrollByAdmin(userName, request));
    }

    @Audited(category = "ACCOUNT", action = "FACE_REMOVE", targetType = "ACCOUNT", targetPathVariable = "userName")
    @DeleteMapping("/{userName}/face")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable String userName) {
        return result(faceAuthService.remove(userName));
    }

    private static ResponseEntity<ApiResponse<Void>> result(FaceActionResult r) {
        if (!r.success()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(r.message()));
        }
        return ResponseEntity.ok(ApiResponse.success(null, r.message()));
    }
}
