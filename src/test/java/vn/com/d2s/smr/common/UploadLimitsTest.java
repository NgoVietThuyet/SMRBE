package vn.com.d2s.smr.common;

import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.dto.sy.PublicSettingsResponse;
import vn.com.d2s.smr.service.sy.SettingService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadLimitsTest {

    @Test
    void followsSystemSettings() {
        SettingService settings = mock(SettingService.class);
        when(settings.getPublicSettings()).thenReturn(new PublicSettingsResponse(5, List.of("pdf", ".DOCX", ".html")));

        UploadLimits limits = UploadLimits.from(settings);

        assertThat(limits.maxMb()).isEqualTo(5);
        assertThatCode(() -> limits.check("bao-cao.PDF", 1024)).doesNotThrowAnyException();
        assertThatCode(() -> limits.check("bien-ban.docx", 5L * 1024 * 1024)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limits.check("lon.pdf", 5L * 1024 * 1024 + 1)).hasMessageContaining("5MB");
        assertThatThrownBy(() -> limits.check("anh.png", 10)).hasMessageContaining(".png");
        // Đuôi có thể chạy mã luôn bị chặn dù cấu hình cho phép.
        assertThatThrownBy(() -> limits.check("trang.html", 10)).hasMessageContaining(".html");
        assertThatThrownBy(() -> limits.check("khongduoi", 10)).hasMessageContaining("không có");
    }

    @Test
    void fallsBackToDefaultsWhenSettingsUnavailable() {
        SettingService broken = mock(SettingService.class);
        when(broken.getPublicSettings()).thenThrow(new IllegalStateException("db"));

        assertThat(UploadLimits.from(broken).maxMb()).isEqualTo(UploadLimits.DEFAULT_MAX_MB);
        assertThat(UploadLimits.from(null).allowedExtensions()).contains(".pdf", ".png");
    }
}
