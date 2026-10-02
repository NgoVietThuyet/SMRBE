package vn.com.d2s.smr.service.cf.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
import vn.com.d2s.smr.dto.cf.file.MeetingFileItemResponse;
import vn.com.d2s.smr.dto.cf.file.UploadedFileResponse;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.service.cf.ObjectStorageService;
import vn.com.d2s.smr.service.mt.MeetingEventPublisher;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-30T01:00:00Z");

    @Mock
    private CmFileRepository cmFileRepository;

    @Mock
    private MeetingInfoRepository meetingInfoRepository;

    @Mock
    private MeetingPersonalRepository meetingPersonalRepository;

    @Mock
    private ObjectStorageService storageService;

    @Mock
    private MeetingEventPublisher eventPublisher;

    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileServiceImpl(
                cmFileRepository,
                meetingInfoRepository,
                meetingPersonalRepository,
                storageService,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void uploadFilesRecordSavesRecordingFiles() {
        MeetingInfo meeting = new MeetingInfo();
        meeting.setId("m1");
        meeting.setReferenceFileId("ref1");
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(storageService.getBucketName()).thenReturn("smr-bucket");

        MockMultipartFile file = new MockMultipartFile(
                "files", "rec.mp4", "video/mp4", "test content".getBytes()
        );

        List<UploadedFileResponse> responses = fileService.uploadFilesRecord("m1", List.of(file));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).fileName()).isEqualTo("rec.mp4");
        verify(storageService).upload(any(), any(InputStream.class), anyLong(), eq("video/mp4"));
        verify(cmFileRepository).save(any(CmFile.class));
    }

    @Test
    void uploadFilesRecordThrowsWhenMeetingNotFound() {
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("files", "rec.mp4", "video/mp4", "content".getBytes());

        assertThatThrownBy(() -> fileService.uploadFilesRecord("m1", List.of(file)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Không tìm thấy cuộc họp.");
    }

    @Test
    void uploadFilesRecordThrowsWhenInvalidExtension() {
        MeetingInfo meeting = new MeetingInfo();
        meeting.setId("m1");
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));

        MockMultipartFile file = new MockMultipartFile("files", "doc.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> fileService.uploadFilesRecord("m1", List.of(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File doc.pdf không hợp lệ.");
    }

    @Test
    void uploadMeetingFileSavesDocumentFile() {
        MeetingInfo meeting = new MeetingInfo();
        meeting.setId("m1");
        meeting.setReferenceFileId("ref1");
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.existsByMeetingIdAndUserName("m1", "user1")).thenReturn(true);
        when(storageService.getBucketName()).thenReturn("smr-bucket");

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "pdf data".getBytes()
        );

        MeetingFileItemResponse response = fileService.uploadMeetingFile("user1", "m1", file);

        assertThat(response.fileName()).isEqualTo("doc.pdf");
        assertThat(response.type()).isEqualTo(1);
        verify(storageService).upload(any(), any(InputStream.class), anyLong(), eq("application/pdf"));
        verify(cmFileRepository).save(any(CmFile.class));
        verify(eventPublisher).publishFilesChanged(eq("m1"), any(), eq("added"));
    }

    @Test
    void uploadMeetingFileThrowsWhenNotMember() {
        MeetingInfo meeting = new MeetingInfo();
        meeting.setId("m1");
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.existsByMeetingIdAndUserName("m1", "user1")).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

        assertThatThrownBy(() -> fileService.uploadMeetingFile("user1", "m1", file))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Bạn không phải thành viên cuộc họp này.");
    }

    @Test
    void uploadMeetingFileDeletesStorageObjectWhenDBSaveFails() {
        MeetingInfo meeting = new MeetingInfo();
        meeting.setId("m1");
        meeting.setReferenceFileId("ref1");
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.existsByMeetingIdAndUserName("m1", "user1")).thenReturn(true);
        when(cmFileRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

        assertThatThrownBy(() -> fileService.uploadMeetingFile("user1", "m1", file))
                .isInstanceOf(RuntimeException.class);

        verify(storageService).delete(any());
    }

    @Test
    void getMeetingFilesReturnsFilesForMember() {
        MeetingInfo meeting = new MeetingInfo();
        meeting.setId("m1");
        meeting.setReferenceFileId("ref1");
        when(meetingPersonalRepository.existsByMeetingIdAndUserName("m1", "user1")).thenReturn(true);
        when(meetingInfoRepository.findById("m1")).thenReturn(Optional.of(meeting));

        CmFile file1 = new CmFile();
        file1.setId("f1");
        file1.setFileName("doc.pdf");
        file1.setFileSize(BigDecimal.valueOf(100));
        file1.setMimeType("application/pdf");
        file1.setType(1);
        file1.setCreateDate(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));

        when(cmFileRepository.findByReferenceFileIdOrderByCreateDateDesc("ref1")).thenReturn(List.of(file1));

        List<MeetingFileItemResponse> files = fileService.getMeetingFiles("user1", "m1");

        assertThat(files).hasSize(1);
        assertThat(files.get(0).id()).isEqualTo("f1");
    }

    @Test
    void getDownloadUrlReturnsPresignedUrl() {
        CmFile file = new CmFile();
        file.setId("f1");
        file.setReferenceFileId("ref1");
        file.setObjectName("meetings/m1/docs/f1.pdf");

        MeetingInfo meeting = new MeetingInfo();
        meeting.setId("m1");

        when(cmFileRepository.findById("f1")).thenReturn(Optional.of(file));
        when(meetingInfoRepository.findFirstByReferenceFileId("ref1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.existsByMeetingIdAndUserName("m1", "user1")).thenReturn(true);
        when(storageService.getDownloadUrl("meetings/m1/docs/f1.pdf", 900)).thenReturn("http://storage/download/f1");

        FileDownloadResponse response = fileService.getDownloadUrl("user1", "f1");

        assertThat(response.url()).isEqualTo("http://storage/download/f1");
        assertThat(response.expiresIn()).isEqualTo(900);
    }
}
