package vn.com.d2s.smr.service.ad;

import vn.com.d2s.smr.entity.ad.AdAccount;

import java.util.Optional;

public interface TokenService {

    TokenPair issueFor(AdAccount account);

    Optional<String> resolveRefreshTokenOwner(String refreshToken);

    String issueResetToken(AdAccount account);

    String issueForGuest(String guestUserName, String displayName, String meetingId);

    Optional<ResetTokenIdentity> resolveResetToken(String resetToken);
}
