package vn.com.d2s.smr.controller.ad;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.dto.ad.contact.ContactResponse;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.service.ad.ContactService;

/** P15 — Danh bạ (chỉ đọc, mọi người đã đăng nhập). */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResultResponse<ContactResponse>>> search(
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.success(contactService.search(organizationId, keyword, page, pageSize), null));
    }
}
