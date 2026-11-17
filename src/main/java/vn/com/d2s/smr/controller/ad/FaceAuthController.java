package vn.com.d2s.smr.controller.ad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.config.audit.Audited;
import vn.com.d2s.smr.dto.ad.auth.AuthResultResponse;
import vn.com.d2s.smr.dto.ad.face.FaceActionResult;
import vn.com.d2s.smr.dto.ad.face.FaceChallengeResponse;
import vn.com.d2s.smr.dto.ad.face.FaceEnrollRequest;
import vn.com.d2s.smr.dto.ad.face.FaceJoinRequest;
import vn.com.d2s.smr.dto.ad.face.FaceLoginRequest;
import vn.com.d2s.smr.dto.ad.face.FaceStatusResponse;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.service.ad.FaceAuthService;
import vn.com.d2s.smr.service.ad.face.FaceEngineException;

import java.security.Principal;

/** Đăng nhập / vào họp / đăng ký bằng khuôn mặt. Chi tiết quyết định ở {@link FaceAuthService}. */
@RestController
@RequestMapping("/api/Auth")
public class FaceAuthController {

    private final FaceAuthService faceAuthService;

    public FaceAuthController(FaceAuthService faceAuthService) {
        this.faceAuthService = faceAuthService;
    }

    @GetMapping("/FaceChallenge")
    public ResponseEntity<ApiResponse<FaceChallengeResponse>> challenge(HttpServletRequest http) {
        try {
            return ResponseEntity.ok(ApiResponse.success(faceAuthService.challenge(clientIp(http)), null));
        } catch (FaceEngineException e) {
            return ResponseEntity.status(503).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @Audited(category = "AUTH", action = "FACE_LOGIN", targetType = "ACCOUNT", targetBodyField = "userName")
    @PostMapping("/FaceLogin")
    public ResponseEntity<ApiResponse<AuthResultResponse>> login(@Valid @RequestBody FaceLoginRequest request, HttpServletRequest http) {
        return auth(faceAuthService.login(request, clientIp(http)));
    }

    @Audited(category = "AUTH", action = "FACE_JOIN", targetType = "MEETING", targetBodyField = "roomCode")
    @PostMapping("/FaceJoin")
    public ResponseEntity<ApiResponse<AuthResultResponse>> join(@Valid @RequestBody FaceJoinRequest request, HttpServletRequest http) {
        return auth(faceAuthService.join(request, clientIp(http)));
    }

    @Audited(category = "AUTH", action = "FACE_ENROLL", targetType = "ACCOUNT")
    @PostMapping("/FaceEnroll")
    public ResponseEntity<ApiResponse<Void>> enroll(Principal principal, @Valid @RequestBody FaceEnrollRequest request) {
        return action(faceAuthService.enroll(principal.getName(), request));
    }

    @GetMapping("/FaceStatus")
    public ResponseEntity<ApiResponse<FaceStatusResponse>> status(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(faceAuthService.status(principal.getName()), null));
    }

    @Audited(category = "AUTH", action = "FACE_REMOVE", targetType = "ACCOUNT")
    @DeleteMapping("/FaceRemove")
    public ResponseEntity<ApiResponse<Void>> remove(Principal principal) {
        return action(faceAuthService.remove(principal.getName()));
    }

    private static ResponseEntity<ApiResponse<AuthResultResponse>> auth(AuthResultResponse result) {
        if (!result.success()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
        }
        return ResponseEntity.ok(ApiResponse.success(result, result.message()));
    }

    private static ResponseEntity<ApiResponse<Void>> action(FaceActionResult result) {
        if (!result.success()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
        }
        return ResponseEntity.ok(ApiResponse.success(null, result.message()));
    }

    /** IP khách: giá trị ĐẦU của X-Forwarded-For (nginx / Caddy đặt), nếu không có thì địa chỉ kết nối. */
    private static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
