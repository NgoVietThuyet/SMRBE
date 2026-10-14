package vn.com.d2s.smr.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Token khách (Auth/GuestSession, claim IsGuest + MeetingId) chỉ được gọi API của đúng cuộc họp đó
 * (phòng họp, chat, tài liệu). Tài khoản thường: chỉ cần đã đăng nhập.
 */
@Component
public class GuestScope implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        // Người chưa đăng nhập là AnonymousAuthenticationToken (isAuthenticated() vẫn true) → phải chặn rõ ràng.
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new AuthorizationDecision(false);
        }
        if (!(auth instanceof JwtAuthenticationToken token)) {
            return new AuthorizationDecision(true);
        }
        Jwt jwt = token.getToken();
        if (!"true".equalsIgnoreCase(jwt.getClaimAsString("IsGuest"))) {
            return new AuthorizationDecision(true);
        }
        return new AuthorizationDecision(allowed(context.getRequest(), jwt.getClaimAsString("MeetingId")));
    }

    static boolean allowed(HttpServletRequest request, String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String meetingBase = "/api/Meeting/" + meetingId;
        return path.equals(meetingBase)
                || path.startsWith(meetingBase + "/")
                || path.equals("/api/File/GetMeetingFiles/" + meetingId)
                || path.equals("/api/File/Meetings/" + meetingId + "/files")
                || path.startsWith("/api/File/Download/");
    }
}
