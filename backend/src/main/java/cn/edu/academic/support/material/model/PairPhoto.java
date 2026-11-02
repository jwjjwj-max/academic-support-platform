package cn.edu.academic.support.material.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("pair_photo")
public class PairPhoto {
    @TableId(type = IdType.AUTO) private Long id;
    private Long pairId;
    private Long uploaderId;
    private String storageKey;
    private String originalFilename;
    private String mimeType;
    private Long fileSize;
    private String description;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long peerConfirmedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime peerConfirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long reviewedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime reviewedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String rejectionReason;
    private Integer revisionNo;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
