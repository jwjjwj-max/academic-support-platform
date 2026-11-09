package cn.edu.academic.support.application.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("application")
public class ParticipationApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long periodId;
    private Long studentUserId;
    private String participantRole;
    private BigDecimal cumulativeGpa;
    private String subjectsJson;
    private String statementText;
    private String learningStylesJson;
    private String personalitiesJson;
    private String applicationStatus;
    private String selectionStatus;
    private Integer revision;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejectionReason;
    private LocalDateTime submittedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reviewedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
