package vn.com.d2s.smr.controller.cf;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
import vn.com.d2s.smr.dto.cf.file.MeetingFileItemResponse;
import vn.com.d2s.smr.dto.cf.file.UploadedFileResponse;
import vn.com.d2s.smr.service.cf.FileService;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/File")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/UploadFilesRecord")
    public ResponseEntity<?> uploadFilesRecord(
            @RequestParam("meetingId") String meetingId,
            @RequestParam("files") List<MultipartFile> files
    ) {
        try {
            List<UploadedFileResponse> result = fileService.uploadFilesRecord(meetingId, files);
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/Meetings/{meetingId}/files")
    public ResponseEntity<?> uploadMeetingFile(
            Principal principal,
            @PathVariable("meetingId") String meetingId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            MeetingFileItemResponse result = fileService.uploadMeetingFile(principal.getName(), meetingId, file);
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/GetMeetingFiles/{meetingId}")
    public ResponseEntity<?> getMeetingFiles(
            Principal principal,
            @PathVariable("meetingId") String meetingId
    ) {
        try {
            List<MeetingFileItemResponse> result = fileService.getMeetingFiles(principal.getName(), meetingId);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/Download/{fileId}")
    public ResponseEntity<?> download(
            Principal principal,
            @PathVariable("fileId") String fileId
    ) {
        try {
            FileDownloadResponse result = fileService.getDownloadUrl(principal.getName(), fileId);
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}
