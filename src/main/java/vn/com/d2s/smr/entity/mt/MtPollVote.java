package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Phiếu bầu: mỗi (PollId, UserName) một dòng. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtPollVotes")
public class MtPollVote {

    @Id
    @Column(name = "Id", nullable = false, length = 100)
    private String id;

    @Column(name = "PollId", nullable = false, length = 100)
    private String pollId;

    @Column(name = "UserName", nullable = false, length = 450)
    private String userName;

    @Column(name = "OptionIndex", nullable = false)
    private int optionIndex;

    @Column(name = "VotedAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime votedAt;
}
