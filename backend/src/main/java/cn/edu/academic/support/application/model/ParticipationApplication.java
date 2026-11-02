package cn.edu.academic.support.application.model;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
