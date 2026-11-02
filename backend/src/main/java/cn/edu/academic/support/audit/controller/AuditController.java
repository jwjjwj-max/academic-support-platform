package cn.edu.academic.support.audit.controller;

import cn.edu.academic.support.audit.mapper.OperationLogMapper;
import cn.edu.academic.support.audit.model.OperationLog;
import cn.edu.academic.support.common.api.ApiResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
public class AuditController {
    private final OperationLogMapper mapper;
    @GetMapping("/admin/audit-logs") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<OperationLog>> list(@RequestParam(required=false) String action,
                                                @RequestParam(required=false) String targetType,
                                                @RequestParam(defaultValue="100") int limit) {
        int safeLimit=Math.max(1,Math.min(limit,500));
        var query=Wrappers.<OperationLog>lambdaQuery().orderByDesc(OperationLog::getCreatedAt).last("LIMIT "+safeLimit);
        if(action!=null&&!action.isBlank()) query.eq(OperationLog::getAction,action.trim());
        if(targetType!=null&&!targetType.isBlank()) query.eq(OperationLog::getTargetType,targetType.trim());
        return ApiResponse.success(mapper.selectList(query));
    }
}
