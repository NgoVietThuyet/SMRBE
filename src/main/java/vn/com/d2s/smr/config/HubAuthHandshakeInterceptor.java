package vn.com.d2s.smr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import vn.com.d2s.smr.service.ad.impl.JwtTokenService;

import java.util.Map;

/**
 * Xác thực kết nối WebSocket /meetinghub.
 * Client SignalR gửi JWT qua query "access_token" (trình duyệt không đặt được header cho WebSocket).
 * Token hợp lệ (cùng JwtDecoder + AccountTokenValidator với REST) → lưu userName vào session;
 * sai/hết hạn → từ chối bắt tay (401).
 */
@Component
public class HubAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_NAME = "smr.userName";
    public static final String ATTR_GUEST = "smr.guest";

    private final JwtDecoder jwtDecoder;
    private final boolean requireAuth;

    public HubAuthHandshakeInterceptor(
            JwtDecoder jwtDecoder,
            @Value("${smr.hub.require-auth:true}") boolean requireAuth
    ) {
        this.jwtDecoder = jwtDecoder;
        this.requireAuth = requireAuth;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("access_token");
        if (token != null && !token.isBlank()) {
            try {
                Jwt jwt = jwtDecoder.decode(java.net.URLDecoder.decode(token, java.nio.charset.StandardCharsets.UTF_8));
                String userName = jwt.getClaimAsString(JwtTokenService.NAME_CLAIM);
                if (userName != null && !userName.isBlank()) {
                    attributes.put(ATTR_USER_NAME, userName);
                    attributes.put(ATTR_GUEST, "true".equalsIgnoreCase(jwt.getClaimAsString("IsGuest")));
                    return true;
                }
            } catch (JwtException e) {
                // rơi xuống nhánh từ chối
            }
        }
        if (!requireAuth) {
            return true; // chế độ tương thích cũ (chỉ dùng khi debug)
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
