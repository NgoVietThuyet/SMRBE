package vn.com.d2s.smr.service.ad;

import vn.com.d2s.smr.dto.ad.auth.AuthResultResponse;
import vn.com.d2s.smr.dto.ad.face.FaceActionResult;
import vn.com.d2s.smr.dto.ad.face.FaceChallengeResponse;
import vn.com.d2s.smr.dto.ad.face.FaceEnrollRequest;
import vn.com.d2s.smr.dto.ad.face.FaceJoinRequest;
import vn.com.d2s.smr.dto.ad.face.FaceLoginRequest;
import vn.com.d2s.smr.dto.ad.face.FaceStatusResponse;

/** Đăng ký, đăng nhập (1:1) và vào họp (1:N trong thành viên) bằng khuôn mặt. */
public interface FaceAuthService {

    FaceChallengeResponse challenge(String ip);

    AuthResultResponse login(FaceLoginRequest request, String ip);

    AuthResultResponse join(FaceJoinRequest request, String ip);

    FaceActionResult enroll(String userName, FaceEnrollRequest request);

    FaceStatusResponse status(String userName);

    FaceActionResult remove(String userName);
}
