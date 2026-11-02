package cn.edu.academic.support.matching.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rejected_match_pair")
public class RejectedMatchPair {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long periodId;
    private Long navigatorUserId;
    private Long climberUserId;
    private Long rejectedBy;
    private String reason;
    private Boolean active;
    private Long releasedBy;
    private LocalDateTime releasedAt;
    private String releaseReason;
    private LocalDateTime createdAt;
}
