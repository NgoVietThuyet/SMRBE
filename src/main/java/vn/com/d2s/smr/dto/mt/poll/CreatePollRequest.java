package vn.com.d2s.smr.dto.mt.poll;

import java.util.List;

public record CreatePollRequest(String question, List<String> options, boolean anonymous) {
}
