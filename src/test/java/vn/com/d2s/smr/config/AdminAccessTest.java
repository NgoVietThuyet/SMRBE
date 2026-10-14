package vn.com.d2s.smr.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccessTest {

    @Mock private AdAccountRepository accountRepository;

    @Test
    void allowsSuperAdminCaseInsensitive() {
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account("admin", "USER,super_admin")));
        AdminAccess access = new AdminAccess(accountRepository);

        assertThat(access.check(() -> authenticated("admin"), null).isGranted()).isTrue();
    }

    @Test
    void deniesNormalUserMissingAccountAndAnonymous() {
        when(accountRepository.findById("user01")).thenReturn(Optional.of(account("user01", "USER")));
        when(accountRepository.findById("ghost")).thenReturn(Optional.empty());
        AdminAccess access = new AdminAccess(accountRepository);

        assertThat(access.check(() -> authenticated("user01"), null).isGranted()).isFalse();
        assertThat(access.check(() -> authenticated("ghost"), null).isGranted()).isFalse();
        assertThat(access.check(() -> null, null).isGranted()).isFalse();
    }

    @Test
    void deniesNullRoleCodes() {
        when(accountRepository.findById("user02")).thenReturn(Optional.of(account("user02", null)));
        AdminAccess access = new AdminAccess(accountRepository);

        assertThat(access.isAdmin("user02")).isFalse();
    }

    private static TestingAuthenticationToken authenticated(String userName) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(userName, null);
        token.setAuthenticated(true);
        return token;
    }

    private static AdAccount account(String userName, String roleCodes) {
        AdAccount account = new AdAccount();
        account.setUserName(userName);
        account.setRoleCodes(roleCodes);
        return account;
    }
}
