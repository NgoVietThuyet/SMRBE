package vn.com.d2s.smr.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto;

import static org.assertj.core.api.Assertions.assertThat;

/** FE gửi và nhận phụ đề với trường "isFinal" — phải đọc / ghi đúng tên này. */
class MeetingCaptionDtoJsonTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void readsAndWritesIsFinal() throws Exception {
        String json = "{\"id\":\"s1\",\"meetingId\":\"m1\",\"text\":\"xin chao\",\"language\":\"vi-VN\",\"isFinal\":true,"
                + "\"timestamp\":\"2026-10-11T03:00:00Z\"}";
        MeetingCaptionDto dto = mapper.readValue(json, MeetingCaptionDto.class);
        assertThat(dto.isFinal()).isTrue();
        assertThat(mapper.writeValueAsString(dto)).contains("\"isFinal\":true").doesNotContain("\"final\"");
    }
}
