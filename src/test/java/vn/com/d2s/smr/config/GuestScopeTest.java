package vn.com.d2s.smr.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.assertj.core.api.Assertions.assertThat;

class GuestScopeTest {

    private static boolean allowed(String path) {
        return GuestScope.allowed(new MockHttpServletRequest("GET", path), "m1");
    }

    @Test
    void guestTokenOnlyReachesItsOwnMeeting() {
        assertThat(allowed("/api/Meeting/m1/room/enter")).isTrue();
        assertThat(allowed("/api/Meeting/m1/messages")).isTrue();
        assertThat(allowed("/api/Meeting/m1")).isTrue();
        assertThat(allowed("/api/File/GetMeetingFiles/m1")).isTrue();
        assertThat(allowed("/api/File/Download/f1")).isTrue();

        assertThat(allowed("/api/Meeting/m2/room/enter")).isFalse();
        assertThat(allowed("/api/Meeting/m1x/room/enter")).isFalse();
        assertThat(allowed("/api/Meeting/query")).isFalse();
        assertThat(allowed("/api/User/Search")).isFalse();
        assertThat(allowed("/api/Task/query")).isFalse();
    }

    @Test
    void anonymousIsDenied() {
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        var ctx = new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/api/contacts"));

        assertThat(new GuestScope().check(() -> anonymous, ctx).isGranted()).isFalse();
        assertThat(new GuestScope().check(() -> null, ctx).isGranted()).isFalse();
    }
}
