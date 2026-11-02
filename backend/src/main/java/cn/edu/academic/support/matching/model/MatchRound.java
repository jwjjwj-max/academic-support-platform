package cn.edu.academic.support.matching.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("match_round")
public class MatchRound {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long periodId;
    private Integer roundNo;
    private String roundType;
    private String status;
    private String weightSnapshot;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
