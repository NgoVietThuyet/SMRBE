package vn.com.d2s.smr.service.cf;

import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
import vn.com.d2s.smr.dto.cf.file.MeetingFileItemResponse;
import vn.com.d2s.smr.dto.cf.file.UploadedFileResponse;

import java.util.List;

public interface FileService {
    List<UploadedFileResponse> uploadFilesRecord(String meetingId, List<MultipartFile> files);
    /** Ghi hình cuộc họp do chủ trì ghi trên trình duyệt (MediaRecorder) — một phần của bản ghi. */
    UploadedFileResponse uploadMeetingRecording(String user, String meetingId, MultipartFile file);
    MeetingFileItemResponse uploadMeetingFile(String user, String meetingId, MultipartFile file);
    List<MeetingFileItemResponse> getMeetingFiles(String user, String meetingId);
    FileDownloadResponse getDownloadUrl(String user, String fileId);
}
