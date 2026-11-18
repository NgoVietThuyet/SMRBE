package vn.com.d2s.smr.service.mt.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;
import vn.com.d2s.smr.entity.mt.MtMeetingAnalysis;
import vn.com.d2s.smr.entity.mt.MtRagChunk;
import vn.com.d2s.smr.service.mt.LlmClientService;
import vn.com.d2s.smr.service.mt.minutes.MinutesAiGenerator;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.CleanTurn;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.DocDigest;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.Glossary;
import vn.com.d2s.smr.service.mt.pipeline.embedding.EmbeddingClient;
import vn.com.d2s.smr.service.mt.pipeline.embedding.EmbeddingException;
import vn.com.d2s.smr.service.mt.pipeline.embedding.VectorCodec;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pipeline sau họp: (1) từ điển từ khóa từ tài liệu + làm sạch transcript → (2) trích diễn biến / quyết định / công việc
 * → (3) lưu bền phân tích và các đoạn RAG kèm embedding. Bước 4 (truy vấn kép) nằm ở trợ lý AI.
 * Lưu trữ / embedding lỗi không làm hỏng biên bản; riêng bước trích xuất lỗi thì ném IllegalStateException để người gọi dùng luồng cũ.
 */
@Service
public class MeetingPipelineService {

    private static final Logger log = LoggerFactory.getLogger(MeetingPipelineService.class);

    public record Input(String meetingId, String meetingName, LocalDateTime startUtc, String agenda,
                        List<MinutesAiGenerator.Participant> participants, List<MeetingTranscriptSegment> segments,
                        List<MinutesAiGenerator.Doc> docs, String sourceHash,
                        List<vn.com.d2s.smr.dto.mt.poll.PollSummary> polls) {
        public Input {
            polls = polls == null ? List.of() : polls;
        }

        public Input(String meetingId, String meetingName, LocalDateTime startUtc, String agenda,
                     List<MinutesAiGenerator.Participant> participants, List<MeetingTranscriptSegment> segments,
                     List<MinutesAiGenerator.Doc> docs, String sourceHash) {
            this(meetingId, meetingName, startUtc, agenda, participants, segments, docs, sourceHash, List.of());
        }
    }

    private final LlmClientService llm;
    private final ObjectMapper mapper;
    private final MeetingIndexStore store;
    private final EmbeddingClient embeddings;
    private final int singleCallChars;

    @Autowired
    public MeetingPipelineService(LlmClientService llm, ObjectMapper mapper, MeetingIndexStore store, EmbeddingClient embeddings,
                                  @Value("${smr.pipeline.max-single-call-chars:40000}") int singleCallChars) {
        this.llm = llm;
        this.mapper = mapper;
        this.store = store;
        this.embeddings = embeddings;
        this.singleCallChars = Math.max(2_000, singleCallChars);
    }

    public MinutesContent run(Input in) {
        // 1. Từ điển chuẩn + làm sạch transcript.
        List<String> names = in.participants().stream().map(MinutesAiGenerator.Participant::fullName).toList();
        Glossary glossary = GlossaryExtractor.extract(in.meetingName(), in.agenda(), names, in.docs());
        List<DocDigest> digests = DocSummarizer.summarize(in.docs(), llm);
        List<CleanTurn> turns = TranscriptCleaner.clean(in.segments(), glossary, llm, mapper);
        log.info("Pipeline cuộc họp {}: {} đoạn → {} lượt lời sạch, {} từ khóa, {} tài liệu",
                in.meetingId(), in.segments().size(), turns.size(), glossary.terms().size(), digests.size());

        // 2. Diễn biến trước, chốt sau.
        MinutesContent content = MeetingExtractor.extract(in.meetingName(), in.startUtc(), in.participants(), turns, digests, in.polls(), llm, mapper, singleCallChars);

        // 3. Lưu bền + chỉ mục (không để lỗi ở đây làm hỏng biên bản).
        try {
            persist(in, glossary, digests, turns);
        } catch (RuntimeException e) {
            log.warn("Lưu phân tích / chỉ mục cuộc họp {} lỗi: {}", in.meetingId(), e.getMessage());
        }
        return content;
    }

    private void persist(Input in, Glossary glossary, List<DocDigest> digests, List<CleanTurn> turns) {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        MtMeetingAnalysis a = new MtMeetingAnalysis();
        a.setId(UUID.randomUUID().toString().replace("-", ""));
        a.setMeetingId(in.meetingId());
        a.setSourceHash(in.sourceHash() == null ? "" : in.sourceHash());
        a.setGlossaryJson(json(glossary));
        a.setDocDigestJson(json(digests));
        a.setCleanedJson(json(turns));
        a.setModel(MeetingExtractor.PROMPT_VERSION);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        store.saveAnalysis(a);

        List<RagChunker.Spec> specs = RagChunker.build(turns, in.startUtc(), in.docs());
        List<float[]> vectors = embed(specs);
        List<MtRagChunk> chunks = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            RagChunker.Spec s = specs.get(i);
            MtRagChunk c = new MtRagChunk();
            c.setId(UUID.randomUUID().toString().replace("-", ""));
            c.setMeetingId(in.meetingId());
            c.setKind(s.kind());
            c.setTitle(trunc(s.title(), 300));
            c.setSpeaker(trunc(s.speaker(), 300));
            c.setOffsetText(trunc(s.offset(), 20));
            c.setContent(s.content());
            c.setRefs(s.refs());
            c.setEmbedding(vectors == null ? null : VectorCodec.encode(vectors.get(i)));
            c.setOrd(i);
            c.setCreatedAt(now);
            chunks.add(c);
        }
        store.replaceChunks(in.meetingId(), chunks);
    }

    /** Embedding cho từng đoạn; lỗi hoặc tắt → null (chỉ mục vẫn dùng được bằng từ khóa). */
    private List<float[]> embed(List<RagChunker.Spec> specs) {
        if (specs.isEmpty() || !embeddings.available()) return null;
        try {
            return embeddings.embed(specs.stream().map(s -> s.title() + "\n" + s.content()).toList());
        } catch (EmbeddingException e) {
            log.warn("Không tạo được embedding, chỉ mục chỉ dùng từ khóa: {}", e.getMessage());
            return null;
        }
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("Không ghi được JSON phân tích.", e);
        }
    }

    private static String trunc(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
