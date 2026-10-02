package vn.com.d2s.smr.service.ad;

import vn.com.d2s.smr.entity.ad.AdAccount;

public interface PasswordResetNotifier {

    void send(AdAccount account, String resetToken);
}
