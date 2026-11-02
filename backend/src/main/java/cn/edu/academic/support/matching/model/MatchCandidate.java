package cn.edu.academic.support.matching.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("match_candidate")
public class MatchCandidate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roundId;
    private Long navigatorApplicationId;
    private Long climberApplicationId;
    private BigDecimal totalScore;
    private String scoreDetail;
    private String matchedSubjects;
    private Boolean adminConfirmed;
    private String status;
    private String navigatorDecision;
    private LocalDateTime navigatorDecidedAt;
    private String climberDecision;
    private LocalDateTime climberDecidedAt;
    private String rejectionReason;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
