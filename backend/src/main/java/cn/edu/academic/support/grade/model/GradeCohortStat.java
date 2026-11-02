package cn.edu.academic.support.grade.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("grade_cohort_stat")
public class GradeCohortStat {
    @TableId(type = IdType.AUTO) private Long id;
    private Long importBatchId; private Long periodId; private String stage;
    private String grade; private String major; private Integer studentCount;
    private LocalDateTime createdAt;
}
