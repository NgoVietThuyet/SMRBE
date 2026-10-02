package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.com.d2s.smr.entity.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MeetingMessages")
public class MeetingMessage extends BaseEntity {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "MeetingId", nullable = false, columnDefinition = "nvarchar(max)")
    private String meetingId = "";

    @Column(name = "SenderUserId", nullable = false, columnDefinition = "nvarchar(max)")
    private String senderUserId = "";

    @Column(name = "ReceiverUserId", nullable = false, columnDefinition = "nvarchar(max)")
    private String receiverUserId = "";

    @Column(name = "MessageText", nullable = false, columnDefinition = "nvarchar(max)")
    private String messageText = "";
}

