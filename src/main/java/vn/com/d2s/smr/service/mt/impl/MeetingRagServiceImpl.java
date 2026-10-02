package vn.com.d2s.smr.service.mt.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.com.d2s.smr.config.LlmProperties;
import vn.com.d2s.smr.dto.cf.file.MeetingFileItemResponse;
import vn.com.d2s.smr.dto.mt.ai.MeetingAiChatResponse;
import vn.com.d2s.smr.dto.mt.ai.MeetingRagChunkDto;
import vn.com.d2s.smr.dto.mt.ai.MeetingRagStatusResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.service.cf.FileService;
import vn.com.d2s.smr.service.mt.LlmClientService;
import vn.com.d2s.smr.service.mt.MeetingRagService;
import vn.com.d2s.smr.service.mt.MeetingTranscriptService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MeetingRagServiceImpl implements MeetingRagService {

    private static final Logger log = LoggerFactory.getLogger(MeetingRagServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MeetingInfoRepository meetingInfoRepository;
    private final MeetingPersonalRepository meetingPersonalRepository;
    private final MeetingTaskRepository meetingTaskRepository;
    private final MeetingTranscriptService transcriptService;
    private final FileService fileService;
    private final LlmClientService llmClientService;
    private final LlmProperties llmProperties;

    // Cache in-memory lưu trữ RAG Knowledge Base cho từng cuộc họp
    private final Map<String, MeetingKnowledgeBase> knowledgeBaseStore = new ConcurrentHashMap<>();

    public MeetingRagServiceImpl(
            MeetingInfoRepository meetingInfoRepository,
            MeetingPersonalRepository meetingPersonalRepository,
            MeetingTaskRepository meetingTaskRepository,
            MeetingTranscriptService transcriptService,
            FileService fileService,
            LlmClientService llmClientService,
            LlmProperties llmProperties
    ) {
        this.meetingInfoRepository = meetingInfoRepository;
        this.meetingPersonalRepository = meetingPersonalRepository;
        this.meetingTaskRepository = meetingTaskRepository;
        this.transcriptService = transcriptService;
        this.fileService = fileService;
        this.llmClientService = llmClientService;
        this.llmProperties = llmProperties;
    }

    private record MeetingKnowledgeBase(
            String meetingId,
            LocalDateTime indexedAt,
            List<MeetingRagChunkDto> chunks,
            String sourcesSummary
    ) {}

    @Override
    public MeetingRagStatusResponse getRagStatus(String meetingId) {
        MeetingKnowledgeBase kb = knowledgeBaseStore.get(meetingId);
        if (kb != null) {
            return new MeetingRagStatusResponse(
                    meetingId,
                    true,
                    kb.chunks.size(),
                    kb.indexedAt,
                    kb.sourcesSummary
            );
        }
        return new MeetingRagStatusResponse(
                meetingId,
                false,
                0,
                null,
                "Chưa được đồng bộ chỉ mục"
        );
    }

    @Override
    public MeetingRagStatusResponse syncKnowledgeBase(String meetingId, String currentUserName) {
        log.info("Bắt đầu đồng bộ RAG Knowledge Base cho cuộc họp: {}", meetingId);
        MeetingInfo meeting = meetingInfoRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp với mã: " + meetingId));

        List<MeetingRagChunkDto> chunks = new ArrayList<>();
        int infoCount = 0;
        int participantCount = 0;
        int taskCount = 0;
        int transcriptCount = 0;
        int fileCount = 0;

        // 1. Chunk Thông tin chung cuộc họp
        StringBuilder infoSb = new StringBuilder();
        infoSb.append("Tên cuộc họp: ").append(meeting.getName()).append("\n");
        if (meeting.getExpectedStartTime() != null) {
            infoSb.append("Thời gian dự kiến bắt đầu: ").append(meeting.getExpectedStartTime().format(DATE_TIME_FORMATTER)).append("\n");
        }
        if (meeting.getExpectedEndTime() != null) {
            infoSb.append("Thời gian dự kiến kết thúc: ").append(meeting.getExpectedEndTime().format(DATE_TIME_FORMATTER)).append("\n");
        }
        if (meeting.getStartDate() != null) {
            infoSb.append("Thời gian thực tế bắt đầu: ").append(meeting.getStartDate().format(DATE_TIME_FORMATTER)).append("\n");
        }
        if (meeting.getEndDate() != null) {
            infoSb.append("Thời gian thực tế kết thúc: ").append(meeting.getEndDate().format(DATE_TIME_FORMATTER)).append("\n");
        }
        if (meeting.getMeetContent() != null && !meeting.getMeetContent().isBlank()) {
            infoSb.append("Mục tiêu / Nội dung tóm tắt: ").append(meeting.getMeetContent()).append("\n");
        }
        if (meeting.getAgenda() != null && !meeting.getAgenda().isBlank()) {
            infoSb.append("Nghị trình chi tiết: ").append(meeting.getAgenda()).append("\n");
        }
        if (meeting.getNotes() != null && !meeting.getNotes().isBlank()) {
            infoSb.append("Ghi chú bổ sung: ").append(meeting.getNotes()).append("\n");
        }
        infoSb.append("Trạng thái cuộc họp: ").append(formatStatus(meeting.getStatus()));

        chunks.add(new MeetingRagChunkDto(
                "chunk-info-1",
                "INFO",
                "Thông tin tổng quan cuộc họp",
                null,
                infoSb.toString().trim(),
                meeting.getExpectedStartTime(),
                1.0
        ));
        infoCount++;

        // 2. Chunks Danh sách người tham gia
        List<MeetingPersonal> participants = meetingPersonalRepository.findByMeetingId(meetingId);
        if (participants != null && !participants.isEmpty()) {
            StringBuilder partSb = new StringBuilder();
            partSb.append("Danh sách người tham dự (Tổng cộng: ").append(participants.size()).append(" người):\n");
            for (MeetingPersonal p : participants) {
                partSb.append("- Họ tên: ").append(p.getFullName().isBlank() ? p.getUserName() : p.getFullName());
                partSb.append(" (Tài khoản: ").append(p.getUserName()).append(")");
                if (p.isChairperson()) {
                    partSb.append(" [CHỦ TRÌ]");
                }
                if (p.isJoined()) {
                    partSb.append(" - Đã tham gia phòng");
                    if (p.getJoinTime() != null) {
                        partSb.append(" lúc ").append(p.getJoinTime().format(DATE_TIME_FORMATTER));
                    }
                } else {
                    partSb.append(" - Chưa tham gia");
                }
                if (p.getEmail() != null && !p.getEmail().isBlank()) {
                    partSb.append(" | Email: ").append(p.getEmail());
                }
                partSb.append("\n");
            }

            chunks.add(new MeetingRagChunkDto(
                    "chunk-part-1",
                    "PARTICIPANT",
                    "Danh sách người tham dự & Chủ trì",
                    null,
                    partSb.toString().trim(),
                    LocalDateTime.now(),
                    1.0
            ));
            participantCount = participants.size();
        }

        // 3. Chunks Nhiệm vụ / Công việc (Tasks)
        List<MeetingTask> tasks = meetingTaskRepository.findByMeetingId(meetingId);
        if (tasks != null && !tasks.isEmpty()) {
            StringBuilder taskSb = new StringBuilder();
            taskSb.append("Danh sách nhiệm vụ & công việc được giao trong cuộc họp (Tổng: ").append(tasks.size()).append(" việc):\n");
            for (MeetingTask t : tasks) {
                taskSb.append("• Tiêu đề: ").append(t.getTitle()).append("\n");
                if (t.getDescription() != null && !t.getDescription().isBlank()) {
                    taskSb.append("  Mô tả: ").append(t.getDescription()).append("\n");
                }
                if (t.getAssigneeUserName() != null && !t.getAssigneeUserName().isBlank()) {
                    taskSb.append("  Người phụ trách: ").append(t.getAssigneeUserName()).append("\n");
                }
                if (t.getDueDate() != null) {
                    taskSb.append("  Hạn chót (Deadline): ").append(t.getDueDate().format(DATE_TIME_FORMATTER)).append("\n");
                }
                taskSb.append("  Mức độ ưu tiên: ").append(formatPriority(t.getPriority()))
                        .append(" | Trạng thái: ").append(formatTaskStatus(t.getStatus())).append("\n\n");
            }

            chunks.add(new MeetingRagChunkDto(
                    "chunk-task-1",
                    "TASK",
                    "Nhiệm vụ, Phân công công việc & Hạn chót",
                    null,
                    taskSb.toString().trim(),
                    LocalDateTime.now(),
                    1.0
            ));
            taskCount = tasks.size();
        }

        // 4. Chunks Biên bản thoại / Transcript (Phụ đề)
        List<MeetingCaptionDto> transcripts = transcriptService.getTranscriptsByMeetingId(meetingId);
        if (transcripts != null && !transcripts.isEmpty()) {
            transcriptCount = transcripts.size();
            // Gom nhóm 4 câu thoại liên tiếp thành một semantic chunk để ngữ cảnh mạch lạc
            int groupSize = 4;
            for (int i = 0; i < transcripts.size(); i += groupSize) {
                int end = Math.min(i + groupSize, transcripts.size());
                List<MeetingCaptionDto> group = transcripts.subList(i, end);
                StringBuilder groupSb = new StringBuilder();
                String primarySpeaker = group.get(0).getSpeakerName();
                LocalDateTime firstTimestamp = group.get(0).getTimestamp() != null
                        ? java.time.LocalDateTime.ofInstant(group.get(0).getTimestamp(), java.time.ZoneId.systemDefault())
                        : LocalDateTime.now();

                for (MeetingCaptionDto cap : group) {
                    LocalDateTime dt = cap.getTimestamp() != null
                            ? java.time.LocalDateTime.ofInstant(cap.getTimestamp(), java.time.ZoneId.systemDefault())
                            : null;
                    String timeStr = dt != null ? dt.format(DATE_TIME_FORMATTER) : "";
                    groupSb.append("[").append(timeStr).append("] ")
                            .append(cap.getSpeakerName() != null ? cap.getSpeakerName() : "Người tham gia").append(": ")
                            .append(cap.getText()).append("\n");
                }

                chunks.add(new MeetingRagChunkDto(
                        "chunk-trans-" + (i / groupSize + 1),
                        "TRANSCRIPT",
                        "Biên bản phát biểu thoại (Đoạn " + (i / groupSize + 1) + ")",
                        primarySpeaker,
                        groupSb.toString().trim(),
                        firstTimestamp,
                        0.9
                ));
            }
        }

        // 5. Chunks Danh mục tài liệu cuộc họp (Files)
        try {
            List<MeetingFileItemResponse> files = fileService.getMeetingFiles(currentUserName, meetingId);
            if (files != null && !files.isEmpty()) {
                fileCount = files.size();
                StringBuilder fileSb = new StringBuilder();
                fileSb.append("Danh sách tài liệu đính kèm cuộc họp (Tổng: ").append(files.size()).append(" tệp):\n");
                for (MeetingFileItemResponse f : files) {
                    fileSb.append("- ").append(f.fileName())
                            .append(" (Dung lượng: ").append(f.fileSize()).append(" KB, Loại: ").append(f.mimeType()).append(")\n");
                }
                chunks.add(new MeetingRagChunkDto(
                        "chunk-file-1",
                        "FILE",
                        "Danh sách tài liệu & Hồ sơ cuộc họp",
                        null,
                        fileSb.toString().trim(),
                        LocalDateTime.now(),
                        0.8
                ));
            }
        } catch (Exception e) {
            log.warn("Không thể tải danh sách tệp đính kèm cho meeting {}: {}", meetingId, e.getMessage());
        }

        String summary = String.format("%d thông tin, %d người tham dự, %d nhiệm vụ, %d đoạn phụ đề, %d tài liệu",
                infoCount, participantCount, taskCount, transcriptCount, fileCount);

        MeetingKnowledgeBase kb = new MeetingKnowledgeBase(meetingId, LocalDateTime.now(), chunks, summary);
        knowledgeBaseStore.put(meetingId, kb);
        log.info("Đã đồng bộ RAG cho cuộc họp {}: tổng {} chunks ({})", meetingId, chunks.size(), summary);

        return new MeetingRagStatusResponse(
                meetingId,
                true,
                chunks.size(),
                kb.indexedAt,
                summary
        );
    }

    @Override
    public MeetingAiChatResponse askMeetingAssistant(String meetingId, String currentUserName, String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Câu hỏi không được để trống.");
        }

        // Đảm bảo RAG Knowledge Base đã được lập chỉ mục
        MeetingKnowledgeBase kb = knowledgeBaseStore.get(meetingId);
        if (kb == null || kb.chunks.isEmpty()) {
            syncKnowledgeBase(meetingId, currentUserName);
            kb = knowledgeBaseStore.get(meetingId);
        }

        if (kb == null || kb.chunks.isEmpty()) {
            return MeetingAiChatResponse.fallback(
                    "Cuộc họp này hiện chưa có đủ dữ liệu (thông tin, người tham dự hoặc biên bản thoại) để trả lời.",
                    Collections.emptyList()
            );
        }

        // Tìm kiếm và xếp hạng các chunks liên quan nhất
        List<MeetingRagChunkDto> rankedChunks = rankChunksByRelevance(kb.chunks, query);
        List<MeetingRagChunkDto> topCitations = rankedChunks.stream().limit(4).toList();

        // Xây dựng ngữ cảnh RAG gửi cho LLM
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("CƠ SỞ DỮ LIỆU CUỘC HỌP (RAG KNOWLEDGE BASE):\n");
        contextBuilder.append("=====================================================\n");
        for (MeetingRagChunkDto chunk : rankedChunks) {
            contextBuilder.append("[").append(chunk.sourceTitle()).append("]\n");
            contextBuilder.append(chunk.content()).append("\n\n");
        }
        contextBuilder.append("=====================================================\n");

        String systemPrompt = (llmProperties.systemPrompt() != null ? llmProperties.systemPrompt() : "Bạn là AI của hệ thống TMS")
                + "\n\n"
                + contextBuilder
                + "\n\nHƯỚNG DẪN TRẢ LỜI:\n"
                + "1. Hãy đọc kỹ toàn bộ ngữ cảnh cuộc họp ở trên và trả lời câu hỏi của người dùng một cách chính xác, mạch lạc, bằng tiếng Việt.\n"
                + "2. Nêu rõ tên người nói, vai trò hoặc thời gian nếu câu hỏi liên quan đến phát biểu hay nhiệm vụ.\n"
                + "3. Nếu câu hỏi yêu cầu tóm tắt cuộc họp: hãy tóm tắt các nội dung cốt lõi, quyết định đã thống nhất và phân công công việc (nếu có).\n"
                + "4. Nếu thông tin không có trong biên bản và dữ liệu cuộc họp, hãy trả lời lịch sự rằng không tìm thấy thông tin này trong tài liệu cuộc họp.";

        String answer = llmClientService.generateAnswer(systemPrompt, query);

        if (answer != null && !answer.isBlank()) {
            return MeetingAiChatResponse.success(answer, topCitations, llmProperties.model());
        }

        // Fallback khi không kết nối được LLM server
        log.warn("Không nhận được câu trả lời từ LLM cho meeting {}, tạo phản hồi trích xuất RAG fallback.", meetingId);
        String fallbackAnswer = buildFallbackAnswer(query, rankedChunks);
        return MeetingAiChatResponse.fallback(fallbackAnswer, topCitations);
    }

    private List<MeetingRagChunkDto> rankChunksByRelevance(List<MeetingRagChunkDto> chunks, String query) {
        String normalizedQuery = normalizeText(query);
        String[] keywords = normalizedQuery.split("\\s+");

        List<ScoredChunk> scoredList = new ArrayList<>();
        for (MeetingRagChunkDto chunk : chunks) {
            double score = 0.0;
            String normalizedContent = normalizeText(chunk.content());
            String normalizedTitle = normalizeText(chunk.sourceTitle());

            // Tính điểm khớp từ khóa
            for (String kw : keywords) {
                if (kw.length() < 2) continue;
                if (normalizedContent.contains(kw)) {
                    score += 2.0;
                }
                if (normalizedTitle.contains(kw)) {
                    score += 3.0;
                }
                if (chunk.speakerName() != null && normalizeText(chunk.speakerName()).contains(kw)) {
                    score += 4.0;
                }
            }

            // Ưu tiên câu hỏi tổng quát
            if (isGeneralOverviewQuery(normalizedQuery)) {
                if ("INFO".equals(chunk.sourceType())) score += 5.0;
                if ("TASK".equals(chunk.sourceType())) score += 4.0;
                if ("PARTICIPANT".equals(chunk.sourceType())) score += 3.0;
            }

            scoredList.add(new ScoredChunk(chunk, score));
        }

        // Sắp xếp giảm dần theo điểm liên quan
        scoredList.sort((a, b) -> Double.compare(b.score, a.score));

        return scoredList.stream().map(sc -> new MeetingRagChunkDto(
                sc.chunk.id(),
                sc.chunk.sourceType(),
                sc.chunk.sourceTitle(),
                sc.chunk.speakerName(),
                sc.chunk.content(),
                sc.chunk.timestamp(),
                sc.score
        )).toList();
    }

    private record ScoredChunk(MeetingRagChunkDto chunk, double score) {}

    private boolean isGeneralOverviewQuery(String query) {
        return query.contains("tom tat") || query.contains("noi dung") || query.contains("ket qua")
                || query.contains("co gi") || query.contains("tong quan") || query.contains("summary")
                || query.contains("quyet dinh") || query.contains("nhiem vu");
    }

    private String buildFallbackAnswer(String query, List<MeetingRagChunkDto> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ *Hệ thống đang hoạt động ở chế độ Trích xuất RAG trực tiếp (Máy chủ LLM nội bộ tại ").append(llmProperties.url()).append(" tạm thời chưa kết nối được).*\n\n");
        sb.append("Dưới đây là thông tin trích xuất từ dữ liệu cuộc họp liên quan nhất đến câu hỏi của bạn:\n\n");

        int count = 0;
        for (MeetingRagChunkDto c : chunks) {
            if (count >= 2) break;
            sb.append("**📌 ").append(c.sourceTitle()).append(":**\n");
            sb.append(c.content()).append("\n\n");
            count++;
        }
        sb.append("💡 *Gợi ý: Vui lòng kiểm tra lại kết nối mạng tới máy chủ AI hoặc bấm nút 'Đồng bộ lại RAG' bên dưới.*");
        return sb.toString();
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                .replaceAll("[ìíịỉĩ]", "i")
                .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                .replaceAll("[ùúụủũưừứựửữ]", "u")
                .replaceAll("[ỳýỵỷỹ]", "y")
                .replaceAll("[đ]", "d")
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim();
    }

    private String formatStatus(int status) {
        return switch (status) {
            case 0 -> "Bản nháp";
            case 1 -> "Đã lên lịch";
            case 2 -> "Đang diễn ra";
            case 3 -> "Đã kết thúc";
            case 4 -> "Đã hủy";
            default -> "Không xác định";
        };
    }

    private String formatPriority(int priority) {
        return switch (priority) {
            case 1 -> "Thấp";
            case 2 -> "Trung bình";
            case 3 -> "Cao";
            case 4 -> "Khẩn cấp";
            default -> "Bình thường";
        };
    }

    private String formatTaskStatus(int status) {
        return switch (status) {
            case 0 -> "Chưa bắt đầu";
            case 1 -> "Đang thực hiện";
            case 2 -> "Đã hoàn thành";
            case 3 -> "Tạm hoãn";
            case 4 -> "Đã hủy";
            default -> "Mới";
        };
    }
}
