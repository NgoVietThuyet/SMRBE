package vn.com.d2s.smr.controller.cf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.com.d2s.smr.controller.common.GlobalExceptionHandler;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
import vn.com.d2s.smr.dto.cf.file.MeetingFileItemResponse;
import vn.com.d2s.smr.dto.cf.file.UploadedFileResponse;
import vn.com.d2s.smr.service.cf.FileService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileService fileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadFilesRecordReturns200() throws Exception {
        UploadedFileResponse item = new UploadedFileResponse("f1", "rec.mp4", BigDecimal.valueOf(100), "video/mp4");
        when(fileService.uploadFilesRecord(eq("m1"), any())).thenReturn(List.of(item));

        MockMultipartFile file = new MockMultipartFile("files", "rec.mp4", "video/mp4", "content".getBytes());

        mockMvc.perform(multipart("/api/File/UploadFilesRecord")
                        .file(file)
                        .param("meetingId", "m1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("f1"))
                .andExpect(jsonPath("$[0].fileName").value("rec.mp4"));
    }

    @Test
    void uploadFilesRecordReturns404WhenMeetingNotFound() throws Exception {
        when(fileService.uploadFilesRecord(eq("m1"), any()))
                .thenThrow(new NoSuchElementException("Không tìm thấy cuộc họp."));

        MockMultipartFile file = new MockMultipartFile("files", "rec.mp4", "video/mp4", "content".getBytes());

        mockMvc.perform(multipart("/api/File/UploadFilesRecord")
                        .file(file)
                        .param("meetingId", "m1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy cuộc họp."));
    }

    @Test
    void uploadMeetingFileReturns200() throws Exception {
        MeetingFileItemResponse response = new MeetingFileItemResponse(
                "f1", "doc.pdf", BigDecimal.valueOf(100), "application/pdf", 1, LocalDateTime.now()
        );
        when(fileService.uploadMeetingFile(eq("user1"), eq("m1"), any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

        mockMvc.perform(multipart("/api/File/Meetings/m1/files")
                        .file(file)
                        .principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("f1"))
                .andExpect(jsonPath("$.fileName").value("doc.pdf"));
    }

    @Test
    void uploadMeetingFileReturns403WhenNotMember() throws Exception {
        when(fileService.uploadMeetingFile(eq("user1"), eq("m1"), any()))
                .thenThrow(new SecurityException("Bạn không phải thành viên cuộc họp này."));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

        mockMvc.perform(multipart("/api/File/Meetings/m1/files")
                        .file(file)
                        .principal(() -> "user1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không phải thành viên cuộc họp này."));
    }

    @Test
    void getMeetingFilesReturns200() throws Exception {
        MeetingFileItemResponse item = new MeetingFileItemResponse(
                "f1", "doc.pdf", BigDecimal.valueOf(100), "application/pdf", 1, LocalDateTime.now()
        );
        when(fileService.getMeetingFiles("user1", "m1")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/File/GetMeetingFiles/m1").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("f1"));
    }

    @Test
    void downloadReturns200WithUrl() throws Exception {
        FileDownloadResponse response = new FileDownloadResponse("http://minio/download/f1", 900);
        when(fileService.getDownloadUrl("user1", "f1")).thenReturn(response);

        mockMvc.perform(get("/api/File/Download/f1").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://minio/download/f1"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }
}
