package vn.com.d2s.smr.dto.mt.room;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** contentJson: mảng nét vẽ (toạ độ chuẩn hoá 0..1) do FE định nghĩa; BE chỉ lưu nguyên văn. */
public record WhiteboardResponse(
        int revision,
        String contentJson,
        @JsonProperty("isLocked") boolean locked,
        String updatedBy,
        LocalDateTime updatedAt
) {
}
