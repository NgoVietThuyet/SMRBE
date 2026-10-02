package vn.com.d2s.smr.service.ad.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.dto.ad.user.UserSearchResponse;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.service.ad.UserService;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final AdAccountRepository accountRepository;

    public UserServiceImpl(AdAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSearchResponse> search(String currentUser, String query, int take) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }

        int normalizedTake = Math.clamp(take, 1, 20);
        return accountRepository.searchUsers(currentUser, normalizedQuery, PageRequest.of(0, normalizedTake))
                .stream()
                .map(account -> new UserSearchResponse(
                        account.getUserName(),
                        account.getFullName(),
                        account.getEmail()
                ))
                .toList();
    }
}
