package vn.com.d2s.smr.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSearchTest {

    @Test
    void matchesWithAndWithoutDiacritics() {
        assertThat(TextSearch.matches("nguyen van an", "Nguyễn Văn Ân")).isTrue();
        assertThat(TextSearch.matches("Nguyễn", "Nguyen Van An")).isTrue();
        assertThat(TextSearch.matches("dung", "Trần Dũng")).isTrue();
        assertThat(TextSearch.matches("do", "Đỗ Minh")).isTrue();
        assertThat(TextSearch.matches("HÙNG pham", "Phạm Văn Hùng")).isTrue();
        assertThat(TextSearch.matches("hung", "Phạm Văn Huy")).isFalse();
        assertThat(TextSearch.matches("", "x")).isTrue();
    }

    @Test
    void wordsCanMatchDifferentFields() {
        assertThat(TextSearch.matches("hung ke toan", "Phạm Văn Hùng", "Kế toán trưởng")).isTrue();
    }
}
