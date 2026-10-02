package vn.com.d2s.smr.controller.mt;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/meetinghub")
public class SignalRNegotiateController {

    @PostMapping("/negotiate")
    public ResponseEntity<Map<String, Object>> negotiatePost() {
        return ResponseEntity.ok(createNegotiateResponse());
    }

    @GetMapping("/negotiate")
    public ResponseEntity<Map<String, Object>> negotiateGet() {
        return ResponseEntity.ok(createNegotiateResponse());
    }

    private Map<String, Object> createNegotiateResponse() {
        String connId = UUID.randomUUID().toString().replace("-", "");
        return Map.of(
                "negotiateVersion", 1,
                "connectionId", connId,
                "connectionToken", connId,
                "availableTransports", List.of(
                        Map.of(
                                "transport", "WebSockets",
                                "transferFormats", List.of("Text", "Binary")
                        )
                )
        );
    }
}
