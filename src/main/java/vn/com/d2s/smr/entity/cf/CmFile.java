package vn.com.d2s.smr.entity.cf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.com.d2s.smr.entity.BaseEntity;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CmFiles")
public class CmFile extends BaseEntity {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "FileName", nullable = false, columnDefinition = "nvarchar(max)")
    private String fileName = "";

    @Column(name = "FileSize", nullable = false, precision = 18, scale = 2)
    private BigDecimal fileSize = BigDecimal.ZERO;

    @Column(name = "MimeType", nullable = false, columnDefinition = "nvarchar(max)")
    private String mimeType = "";

    @Column(name = "Extention", nullable = false, columnDefinition = "nvarchar(max)")
    private String extension = "";

    @Column(name = "Type", nullable = false)
    private int type;

    @Column(name = "Icon", nullable = false, columnDefinition = "nvarchar(max)")
    private String icon = "";

    @Column(name = "RefrenceFileId", nullable = false, columnDefinition = "nvarchar(max)")
    private String referenceFileId = "";

    @Column(name = "IsBienBan", nullable = false)
    private boolean minutesFile;

    @Column(name = "OrderNumber", nullable = false)
    private int orderNumber;

    @Column(name = "VoiceToText", nullable = false, columnDefinition = "nvarchar(max)")
    private String voiceToText = "";

    @Column(name = "BucketName", nullable = false, columnDefinition = "nvarchar(max)")
    private String bucketName = "";

    @Column(name = "ObjectName", nullable = false, columnDefinition = "nvarchar(max)")
    private String objectName = "";
}

