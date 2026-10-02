package vn.com.d2s.smr.service.ad.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import vn.com.d2s.smr.dto.ad.user.UserSearchResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private AdAccountRepository accountRepository;

    @Test
    void blankQueryReturnsEmptyWithoutDatabaseCall() {
        UserServiceImpl service = new UserServiceImpl(accountRepository);

        assertThat(service.search("admin", "  ", 10)).isEmpty();

        verify(accountRepository, never()).searchUsers(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }

    @Test
    void searchTrimsQueryClampsTakeAndMapsContract() {
        AdAccount account = new AdAccount();
        account.setUserName("user01");
        account.setFullName("Nguyễn Văn A");
        account.setEmail("user01@example.com");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(accountRepository.searchUsers(eq("admin"), eq("Nguyễn"), pageable.capture()))
                .thenReturn(List.of(account));
        UserServiceImpl service = new UserServiceImpl(accountRepository);

        List<UserSearchResponse> result = service.search("admin", "  Nguyễn ", 100);

        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(result).containsExactly(new UserSearchResponse(
                "user01", "Nguyễn Văn A", "user01@example.com"
        ));
    }
}
