package cn.edu.academic.support.matching.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("support_pair")
public class SupportPair {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long periodId;
    private String pairNo;
    private Long navigatorApplicationId;
    private Long climberApplicationId;
    private Long sourceCandidateId;
    private String matchKind;
    private String status;
    private LocalDateTime confirmedAt;
    private Long completedBy;
    private LocalDateTime completedAt;
    private String acceptanceNote;
    private Long endedBy;
    private LocalDateTime endedAt;
    private String endReason;
    private LocalDateTime createdAt;
}
