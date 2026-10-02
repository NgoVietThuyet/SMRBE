package vn.com.d2s.smr.dto.ad.auth;

public record UserProfileResponse(
        String userName,
        String fullName,
        String email,
        String phone,
        String address,
        String organizationName,
        String titleName
) {
}
