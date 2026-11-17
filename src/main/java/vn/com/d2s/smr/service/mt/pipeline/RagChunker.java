package vn.com.d2s.smr.service.mt.pipeline;

import vn.com.d2s.smr.entity.mt.MtRagChunk;
import vn.com.d2s.smr.service.mt.minutes.MinutesAiGenerator;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.CleanTurn;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bước 3: cắt dữ liệu thành các đoạn để hỏi đáp.
 *  - Transcript: cửa sổ trượt 6 lượt lời, đoạn sau gối lên đoạn trước 2 lượt (không cắt theo số ký tự để khỏi đứt mạch trả lời).
 *  - Tài liệu: theo trang (PDF) hoặc theo nhóm đoạn văn / đề mục (Word, văn bản).
 * Mỗi đoạn có nhãn nguồn: loại dữ liệu, người nói, mốc thời gian, Id đoạn gốc / tên tài liệu + trang.
 */
public final class RagChunker {

    static final int WINDOW_TURNS = 6;
    static final int OVERLAP_TURNS = 2;
    static final int DOC_CHUNK_CHARS = 1_200;

    private RagChunker() {
    }

    public record Spec(String kind, String title, String speaker, String offset, String content, String refs) {
    }

    public static List<Spec> build(List<CleanTurn> turns, LocalDateTime base, List<MinutesAiGenerator.Doc> docs) {
        List<Spec> out = new ArrayList<>();
        out.addAll(transcriptChunks(turns, base));
        if (docs != null) {
            for (MinutesAiGenerator.Doc d : docs) out.addAll(docChunks(d));
        }
        return out;
    }

    static List<Spec> transcriptChunks(List<CleanTurn> turns, LocalDateTime base) {
        List<Spec> out = new ArrayList<>();
        if (turns.isEmpty()) return out;
        LocalDateTime origin = base != null ? base : turns.get(0).at();
        int stride = WINDOW_TURNS - OVERLAP_TURNS;
        for (int start = 0; start < turns.size(); start += stride) {
            int end = Math.min(start + WINDOW_TURNS, turns.size());
            List<CleanTurn> win = turns.subList(start, end);
            StringBuilder text = new StringBuilder();
            Set<String> speakers = new LinkedHashSet<>();
            Set<String> refs = new LinkedHashSet<>();
            for (CleanTurn t : win) {
                text.append("[⏱ ").append(MinutesAiGenerator.offset(origin, t.at())).append("] ").append(t.speaker()).append(": ")
                        .append(t.text().replace('\n', ' ')).append('\n');
                speakers.add(t.speaker());
                refs.addAll(t.segmentIds());
            }
            String from = MinutesAiGenerator.offset(origin, win.get(0).at());
            String to = MinutesAiGenerator.offset(origin, win.get(win.size() - 1).at());
            out.add(new Spec(MtRagChunk.TRANSCRIPT, "Transcript " + from + (from.equals(to) ? "" : "–" + to),
                    String.join(", ", speakers.stream().limit(3).toList()), from, text.toString().trim(), String.join(",", refs)));
            if (end >= turns.size()) break;
        }
        return out;
    }

    static List<Spec> docChunks(MinutesAiGenerator.Doc doc) {
        List<Spec> out = new ArrayList<>();
        String[] pages = doc.text().split("\f");
        boolean paged = pages.length > 1;
        for (int p = 0; p < pages.length; p++) {
            String page = pages[p].trim();
            if (page.isEmpty()) continue;
            int part = 0;
            for (String piece : split(page)) {
                part++;
                String where = paged ? "trang " + (p + 1) : (part > 1 || page.length() > DOC_CHUNK_CHARS ? "phần " + part : "toàn văn");
                out.add(new Spec(MtRagChunk.DOC, "Tài liệu: " + doc.name() + " (" + where + ")", null, null,
                        "[Tài liệu: " + doc.name() + ", " + where + "]\n" + piece, doc.name() + (paged ? "#" + (p + 1) : "")));
            }
        }
        return out;
    }

    /** Chia theo đoạn văn (dòng trống / đề mục), gom tới ~1200 ký tự mỗi đoạn; đoạn văn quá dài thì cắt tại dấu câu. */
    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String para : text.split("\n\\s*\n|\n(?=\\p{Lu}[^\n]{0,80}\n)")) {
            String p = para.trim();
            if (p.isEmpty()) continue;
            while (p.length() > DOC_CHUNK_CHARS) {
                int cut = p.lastIndexOf(". ", DOC_CHUNK_CHARS);
                if (cut < DOC_CHUNK_CHARS / 2) cut = DOC_CHUNK_CHARS;
                else cut += 1;
                if (cur.length() > 0) { out.add(cur.toString().trim()); cur.setLength(0); }
                out.add(p.substring(0, cut).trim());
                p = p.substring(cut).trim();
            }
            if (cur.length() + p.length() > DOC_CHUNK_CHARS && cur.length() > 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            }
            cur.append(p).append("\n\n");
        }
        if (cur.toString().trim().length() > 0) out.add(cur.toString().trim());
        return out;
    }
}
