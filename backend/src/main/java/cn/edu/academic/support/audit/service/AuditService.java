package cn.edu.academic.support.audit.service;

import cn.edu.academic.support.audit.mapper.OperationLogMapper;
import cn.edu.academic.support.audit.model.OperationLog;
import cn.edu.academic.support.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public void log(AuthenticatedUser actor, String action, String targetType, Object targetId,
                    Object before, Object after, String reason, HttpServletRequest request) {
        OperationLog log = new OperationLog();
        log.setActorId(actor == null ? null : actor.id());
        log.setActorLogin(actor == null ? "SYSTEM" : actor.loginName());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId == null ? null : String.valueOf(targetId));
        log.setBeforeSnapshot(json(before));
        log.setAfterSnapshot(json(after));
        log.setReason(reason);
        if (request != null) {
            log.setIpAddress(request.getRemoteAddr());
            log.setUserAgent(trim(request.getHeader("User-Agent"), 500));
        }
        operationLogMapper.insert(log);
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":true}";
        }
    }

    private String trim(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
