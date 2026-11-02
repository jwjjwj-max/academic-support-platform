package cn.edu.academic.support.grade.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @TableName("grade_correction_history")
public class GradeCorrectionHistory {
    @TableId(type = IdType.AUTO) private Long id;
    private Long gradeId; private BigDecimal oldTermGpa; private BigDecimal newTermGpa;
    private Integer oldTermGpaRank; private Integer newTermGpaRank; private String reason;
    private Long operatorId; private LocalDateTime createdAt;
}
