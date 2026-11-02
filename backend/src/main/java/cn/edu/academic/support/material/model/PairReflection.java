package cn.edu.academic.support.material.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("pair_reflection")
public class PairReflection {
    @TableId(type = IdType.AUTO) private Long id;
    private Long pairId;
    private Long authorId;
    private String content;
    private Integer wordCount;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String rejectionReason;
    private LocalDateTime submittedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long reviewedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
