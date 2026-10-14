package vn.com.d2s.smr.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import vn.com.d2s.smr.service.ad.impl.JwtTokenService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HubAuthHandshakeInterceptorTest {

    private final JwtDecoder decoder = token -> {
        if (!"good".equals(token)) {
            throw new BadJwtException("bad");
        }
        return Jwt.withTokenValue(token)
                .header("alg", "HS256")
                .claim(JwtTokenService.NAME_CLAIM, "alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    };

    private boolean handshake(HubAuthHandshakeInterceptor interceptor, String query, Map<String, Object> attrs, MockHttpServletResponse res) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/meetinghub");
        req.setQueryString(query);
        return interceptor.beforeHandshake(new ServletServerHttpRequest(req), new ServletServerHttpResponse(res), null, attrs);
    }

    @Test
    void validTokenStoresUserName() {
        Map<String, Object> attrs = new HashMap<>();
        boolean ok = handshake(new HubAuthHandshakeInterceptor(decoder, true), "id=1&access_token=good", attrs, new MockHttpServletResponse());

        assertThat(ok).isTrue();
        assertThat(attrs).containsEntry(HubAuthHandshakeInterceptor.ATTR_USER_NAME, "alice");
    }

    @Test
    void missingOrInvalidTokenIsRejected() {
        MockHttpServletResponse res = new MockHttpServletResponse();
        HubAuthHandshakeInterceptor interceptor = new HubAuthHandshakeInterceptor(decoder, true);

        assertThat(handshake(interceptor, "id=1", new HashMap<>(), res)).isFalse();
        assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(handshake(interceptor, "access_token=forged", new HashMap<>(), new MockHttpServletResponse())).isFalse();
    }

    @Test
    void compatibilityModeAllowsAnonymous() {
        Map<String, Object> attrs = new HashMap<>();
        assertThat(handshake(new HubAuthHandshakeInterceptor(decoder, false), "id=1", attrs, new MockHttpServletResponse())).isTrue();
        assertThat(attrs).doesNotContainKey(HubAuthHandshakeInterceptor.ATTR_USER_NAME);
    }
}
