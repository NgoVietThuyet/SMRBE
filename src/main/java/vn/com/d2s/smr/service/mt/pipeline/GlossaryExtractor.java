package vn.com.d2s.smr.service.mt.pipeline;

import vn.com.d2s.smr.service.mt.minutes.MinutesAiGenerator;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.Glossary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bước 1a: rút "từ điển chuẩn" từ chính cuộc họp — tên thành viên, tên cuộc họp, và từ tài liệu đính kèm: viết tắt, cụm từ riêng
 * (tên dự án, thuật ngữ) và con số quan trọng. Dùng quy tắc (không gọi LLM) nên rẻ và ổn định.
 */
public final class GlossaryExtractor {

    static final int MAX_NAMES = 40;
    static final int MAX_TERMS = 80;
    private static final Pattern ACRONYM = Pattern.compile("(?<![\\p{L}\\p{N}])[\\p{Lu}\\p{N}]{2,10}(?![\\p{L}\\p{N}])");
    private static final Pattern PHRASE = Pattern.compile("\\p{Lu}[\\p{L}\\p{N}]*(?:\\s+\\p{Lu}[\\p{L}\\p{N}]*){1,4}");
    private static final Pattern NUMBER = Pattern.compile(
            "\\d[\\d.,]*\\s?(?:%|tỷ|triệu|nghìn|ngàn|đồng|VND|USD|ngày|tuần|tháng|quý|năm|người|giờ|phút)", Pattern.CASE_INSENSITIVE);

    private GlossaryExtractor() {
    }

    public static Glossary extract(String meetingName, String agenda, List<String> participantNames, List<MinutesAiGenerator.Doc> docs) {
        Set<String> names = new LinkedHashSet<>();
        if (participantNames != null) {
            for (String n : participantNames) {
                if (n != null && !n.isBlank() && names.size() < MAX_NAMES) names.add(n.trim());
            }
        }
        Map<String, Integer> freq = new LinkedHashMap<>();
        if (meetingName != null && !meetingName.isBlank()) freq.merge(meetingName.trim(), 3, Integer::sum);
        scan(agenda, freq);
        if (docs != null) {
            for (MinutesAiGenerator.Doc d : docs) scan(d.text(), freq);
        }
        List<String> terms = freq.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue(), a.getValue());
                    return c != 0 ? c : Integer.compare(b.getKey().length(), a.getKey().length());
                })
                .map(Map.Entry::getKey)
                .filter(t -> names.stream().noneMatch(n -> n.equalsIgnoreCase(t)))
                .limit(MAX_TERMS)
                .toList();
        return new Glossary(new ArrayList<>(names), terms);
    }

    private static void scan(String text, Map<String, Integer> freq) {
        if (text == null || text.isBlank()) return;
        String t = text.replace('\f', '\n');
        collect(ACRONYM.matcher(t), freq, true);
        collect(PHRASE.matcher(t), freq, false);
        collect(NUMBER.matcher(t), freq, false);
    }

    private static void collect(Matcher m, Map<String, Integer> freq, boolean needLetter) {
        while (m.find()) {
            String s = m.group().trim().replaceAll("\\s+", " ");
            if (s.length() < 2 || s.length() > 60) continue;
            if (needLetter && s.chars().noneMatch(Character::isLetter)) continue;
            // Bỏ viết hoa đầu câu thông thường (một chữ cái hoa rồi chữ thường) đứng một mình; cụm ≥ 2 từ hoặc có số thì giữ.
            freq.merge(s, 1, Integer::sum);
        }
    }

    static String fold(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
