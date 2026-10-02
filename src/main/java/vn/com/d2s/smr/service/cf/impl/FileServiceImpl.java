package vn.com.d2s.smr.service.cf.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
import vn.com.d2s.smr.dto.cf.file.MeetingFileItemResponse;
import vn.com.d2s.smr.dto.cf.file.UploadedFileResponse;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.service.cf.FileService;
import vn.com.d2s.smr.service.cf.ObjectStorageService;
import vn.com.d2s.smr.service.mt.MeetingEventPublisher;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class FileServiceImpl implements FileService {

    private static final Set<String> RECORDING_EXTENSIONS = Set.of(".mp4", ".webm", ".mkv", ".mp3", ".wav", ".ogg");
    private static final long MAX_RECORDING_SIZE = 2L * 1024 * 1024 * 1024; // 2GB

    private static final Set<String> DOC_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv", ".png", ".jpg", ".jpeg", ".zip"
    );
    private static final long MAX_DOC_SIZE = 200L * 1024 * 1024; // 200MB

    private final CmFileRepository cmFileRepository;
    private final MeetingInfoRepository meetingInfoRepository;
    private final MeetingPersonalRepository meetingPersonalRepository;
    private final ObjectStorageService storageService;
    private final MeetingEventPublisher eventPublisher;
    private final Clock clock;

    public FileServiceImpl(
            CmFileRepository cmFileRepository,
            MeetingInfoRepository meetingInfoRepository,
            MeetingPersonalRepository meetingPersonalRepository,
            ObjectStorageService storageService,
            MeetingEventPublisher eventPublisher,
            Clock clock
    ) {
        this.cmFileRepository = cmFileRepository;
        this.meetingInfoRepository = meetingInfoRepository;
        this.meetingPersonalRepository = meetingPersonalRepository;
        this.storageService = storageService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public List<UploadedFileResponse> uploadFilesRecord(String meetingId, List<MultipartFile> files) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy cuộc họp."));

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Không có file recording.");
        }

        List<UploadedFileResponse> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);

        for (MultipartFile file : files) {
            String originalFileName = file.getOriginalFilename();
            String ext = getExtension(originalFileName);

            if (file.getSize() <= 0 || file.getSize() > MAX_RECORDING_SIZE || !RECORDING_EXTENSIONS.contains(ext.toLowerCase())) {
                throw new IllegalArgumentException("File " + originalFileName + " không hợp lệ.");
            }

            String id = UUID.randomUUID().toString().replace("-", "");
            String objectName = "meetings/" + meetingId + "/recordings/" + id + ext.toLowerCase();

            try (InputStream is = file.getInputStream()) {
                storageService.upload(
                        objectName,
                        is,
                        file.getSize(),
                        file.getContentType() != null ? file.getContentType() : "application/octet-stream"
                );
            } catch (IOException e) {
                throw new RuntimeException("Lỗi đọc file upload: " + e.getMessage(), e);
            }

            CmFile entity = new CmFile();
            entity.setId(id);
            entity.setFileName(getCleanFileName(originalFileName));
            entity.setFileSize(BigDecimal.valueOf(file.getSize()));
            entity.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
            entity.setExtension(ext);
            entity.setType(2);
            entity.setIcon("recording");
            entity.setReferenceFileId(meeting.getReferenceFileId());
            entity.setMinutesFile(false);
            entity.setOrderNumber(0);
            entity.setVoiceToText("");
            entity.setBucketName(storageService.getBucketName());
            entity.setObjectName(objectName);
            entity.setCreateBy("jibri");
            entity.setCreateDate(now);
            entity.setUpdateBy("jibri");
            entity.setUpdateDate(now);

            try {
                cmFileRepository.save(entity);
            } catch (Exception ex) {
                storageService.delete(objectName);
                throw ex;
            }

            result.add(new UploadedFileResponse(entity.getId(), entity.getFileName(), entity.getFileSize(), entity.getMimeType()));
        }

        return result;
    }

    @Override
    public MeetingFileItemResponse uploadMeetingFile(String user, String meetingId, MultipartFile file) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy cuộc họp."));

        if (!meetingPersonalRepository.existsByMeetingIdAndUserName(meetingId, user)) {
            throw new SecurityException("Bạn không phải thành viên cuộc họp này.");
        }

        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException("Chưa chọn file.");
        }

        if (file.getSize() > MAX_DOC_SIZE) {
            throw new IllegalArgumentException("File vượt quá 200MB.");
        }

        String originalFileName = file.getOriginalFilename();
        String ext = getExtension(originalFileName);

        if (ext.isEmpty() || !DOC_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new IllegalArgumentException("Định dạng " + ext + " không được hỗ trợ.");
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        String objectName = "meetings/" + meetingId + "/docs/" + id + ext.toLowerCase();

        try (InputStream is = file.getInputStream()) {
            storageService.upload(
                    objectName,
                    is,
                    file.getSize(),
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream"
            );
        } catch (IOException e) {
            throw new RuntimeException("Lỗi đọc file upload: " + e.getMessage(), e);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        CmFile entity = new CmFile();
        entity.setId(id);
        entity.setFileName(getCleanFileName(originalFileName));
        entity.setFileSize(BigDecimal.valueOf(file.getSize()));
        entity.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        entity.setExtension(ext);
        entity.setType(1);
        entity.setIcon("doc");
        entity.setReferenceFileId(meeting.getReferenceFileId());
        entity.setMinutesFile(false);
        entity.setOrderNumber(0);
        entity.setVoiceToText("");
        entity.setBucketName(storageService.getBucketName());
        entity.setObjectName(objectName);
        entity.setCreateBy(user);
        entity.setCreateDate(now);
        entity.setUpdateBy(user);
        entity.setUpdateDate(now);

        try {
            cmFileRepository.save(entity);
        } catch (Exception ex) {
            storageService.delete(objectName);
            throw ex;
        }

        eventPublisher.publishFilesChanged(meetingId, entity.getId(), "added");

        return new MeetingFileItemResponse(
                entity.getId(),
                entity.getFileName(),
                entity.getFileSize(),
                entity.getMimeType(),
                entity.getType(),
                entity.getCreateDate()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingFileItemResponse> getMeetingFiles(String user, String meetingId) {
        if (!meetingPersonalRepository.existsByMeetingIdAndUserName(meetingId, user)) {
            throw new SecurityException("Bạn không phải thành viên cuộc họp này.");
        }

        String referenceFileId = meetingInfoRepository.findById(meetingId)
                .map(MeetingInfo::getReferenceFileId)
                .orElse("");

        return cmFileRepository.findByReferenceFileIdOrderByCreateDateDesc(referenceFileId)
                .stream()
                .map(f -> new MeetingFileItemResponse(
                        f.getId(),
                        f.getFileName(),
                        f.getFileSize(),
                        f.getMimeType(),
                        f.getType(),
                        f.getCreateDate()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResponse getDownloadUrl(String user, String fileId) {
        CmFile file = cmFileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tệp tin."));

        String meetingId = meetingInfoRepository.findFirstByReferenceFileId(file.getReferenceFileId())
                .map(MeetingInfo::getId)
                .orElse(null);

        if (meetingId == null || !meetingPersonalRepository.existsByMeetingIdAndUserName(meetingId, user)) {
            throw new SecurityException("Bạn không phải thành viên cuộc họp này.");
        }

        String url = storageService.getDownloadUrl(file.getObjectName(), 900);
        return new FileDownloadResponse(url, 900);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private String getCleanFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            return fileName.substring(lastSlash + 1);
        }
        return fileName;
    }
}
