package vn.com.d2s.smr.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Tìm kiếm tiếng Việt không phân biệt dấu / hoa thường: "nguyen van an" khớp "Nguyễn Văn Ân", "dung" khớp "Dũng". */
public final class TextSearch {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private TextSearch() {
    }

    public static String fold(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        return MARKS.matcher(n).replaceAll("").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Mọi từ trong query đều xuất hiện trong ít nhất một trường. */
    public static boolean matches(String query, String... fields) {
        String q = fold(query);
        if (q.isEmpty()) {
            return true;
        }
        StringBuilder hay = new StringBuilder();
        for (String f : fields) {
            hay.append(' ').append(fold(f));
        }
        String text = hay.toString();
        for (String word : q.split(" ")) {
            if (!text.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
