package vn.com.d2s.smr.service.ad;

import vn.com.d2s.smr.dto.ad.user.UserSearchResponse;

import java.util.List;

public interface UserService {

    List<UserSearchResponse> search(String currentUser, String query, int take);
}
