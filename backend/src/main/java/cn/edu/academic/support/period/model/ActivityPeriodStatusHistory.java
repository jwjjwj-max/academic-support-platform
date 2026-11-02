package cn.edu.academic.support.period.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_period_status_history")
public class ActivityPeriodStatusHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long periodId;
    private String oldStatus;
    private String newStatus;
    private String reason;
    private Long operatorId;
    private LocalDateTime createdAt;
}

