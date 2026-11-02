package cn.edu.academic.support.material.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("pair_material_history")
public class PairMaterialHistory {
    @TableId(type = IdType.AUTO) private Long id;
    private Long pairId;
    private String materialType;
    private Long materialId;
    private Integer revisionNo;
    private String action;
    private String reason;
    private String snapshot;
    private Long operatorId;
    private LocalDateTime createdAt;
}
