package cn.edu.academic.support.notification.service;

import cn.edu.academic.support.application.mapper.ParticipationApplicationMapper;
import cn.edu.academic.support.application.model.ParticipationApplication;
import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.matching.mapper.SupportPairMapper;
import cn.edu.academic.support.matching.model.SupportPair;
import cn.edu.academic.support.material.mapper.PairPhotoMapper;
import cn.edu.academic.support.material.mapper.PairReflectionMapper;
import cn.edu.academic.support.material.model.PairPhoto;
import cn.edu.academic.support.material.model.PairReflection;
import cn.edu.academic.support.notification.dto.*;
import cn.edu.academic.support.notification.mapper.*;
import cn.edu.academic.support.notification.model.Notification;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.mapper.UserAccountMapper;
import cn.edu.academic.support.user.model.UserAccount;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationMapper notificationMapper;
    private final NotificationRecipientMapper recipientMapper;
    private final UserAccountMapper userMapper;
    private final ParticipationApplicationMapper applicationMapper;
    private final SupportPairMapper pairMapper;
    private final PairPhotoMapper photoMapper;
    private final PairReflectionMapper reflectionMapper;
    private final ActivityPeriodMapper periodMapper;
    private final AuditService auditService;

    public List<Map<String,Object>> mine(Long userId) { return recipientMapper.selectMine(userId); }
    public long unread(Long userId) { return recipientMapper.unreadCount(userId); }

    @Transactional
    public void read(Long userId, Long id) {
        if (recipientMapper.markRead(id, userId, LocalDateTime.now()) == 0 && notificationMapper.selectById(id) == null)
            throw new BusinessException("NOTIFICATION_NOT_FOUND", "通知不存在", HttpStatus.NOT_FOUND);
    }

    @Transactional
    public Notification create(AuthenticatedUser actor, NotificationCreateRequest body, HttpServletRequest request) {
        Set<Long> recipients;
        if ("ALL_STUDENTS".equals(body.audienceType())) {
            recipients = userMapper.selectList(Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getAccountType, "STUDENT")
                    .eq(UserAccount::getAccountStatus, "ACTIVE")).stream().map(UserAccount::getId).collect(Collectors.toSet());
        } else {
            if (body.periodId() == null) throw new BusinessException("PERIOD_REQUIRED", "期次参与者通知必须选择期次");
            recipients = applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery().eq(ParticipationApplication::getPeriodId, body.periodId())
                    .eq(ParticipationApplication::getApplicationStatus, "APPROVED")).stream().map(ParticipationApplication::getStudentUserId).collect(Collectors.toSet());
        }
        if (recipients.isEmpty()) throw new BusinessException("NO_NOTIFICATION_RECIPIENT", "当前通知范围内没有收件人");
        Notification n = new Notification(); n.setPeriodId(body.periodId()); n.setTitle(body.title().trim()); n.setContent(body.content().trim());
        n.setAudienceType(body.audienceType()); n.setAudienceLabel("ALL_STUDENTS".equals(body.audienceType()) ? "全部学生" : "本期审核通过参与者"); n.setCreatedBy(actor.id());
        notificationMapper.insert(n); recipients.forEach(id -> recipientMapper.insert(n.getId(), id));
        auditService.log(actor, "NOTIFICATION_SEND", "NOTIFICATION", n.getId(), null, Map.of("recipientCount", recipients.size()), null, request);
        return n;
    }

    public List<TodoResponse> studentTodos(Long userId) {
        List<TodoResponse> result = new ArrayList<>();
        Set<Long> appIds = applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery().eq(ParticipationApplication::getStudentUserId, userId))
                .stream().map(ParticipationApplication::getId).collect(Collectors.toSet());
        if (!appIds.isEmpty()) for (SupportPair pair : pairMapper.selectList(Wrappers.<SupportPair>lambdaQuery()
                 .and(q -> q.in(SupportPair::getNavigatorApplicationId, appIds).or().in(SupportPair::getClimberApplicationId, appIds)).eq(SupportPair::getStatus,"ACTIVE"))) {
            ActivityPeriod period = periodMapper.selectById(pair.getPeriodId());
            boolean reviewOpen = period != null && List.of("TRACKING", "ACCEPTANCE").contains(period.getStatus());
            long peer = photoMapper.selectCount(Wrappers.<PairPhoto>lambdaQuery().eq(PairPhoto::getPairId,pair.getId()).ne(PairPhoto::getUploaderId,userId).eq(PairPhoto::getStatus,"PEER_PENDING"));
            if (peer > 0 && reviewOpen) result.add(new TodoResponse("PHOTO_CONFIRM","确认照片真实性","有同伴上传的照片等待你确认",pair.getId(),"/materials",peer));
            PairReflection reflection = reflectionMapper.selectOne(Wrappers.<PairReflection>lambdaQuery().eq(PairReflection::getPairId,pair.getId()).eq(PairReflection::getAuthorId,userId));
            if ((reflection == null || "REJECTED".equals(reflection.getStatus())) && reflectionSubmissionOpen(period))
                result.add(new TodoResponse("REFLECTION","提交活动感悟",reflection == null ? "请提交300—500字活动感悟" : "感悟被驳回，请修改后重提",pair.getId(),"/materials",1));
        }
        long unread = unread(userId); if (unread > 0) result.add(new TodoResponse("NOTIFICATION","未读站内通知","请查看管理员发布的通知",null,"/notifications",unread));
        return result;
    }

    private boolean reflectionSubmissionOpen(ActivityPeriod period) {
        if (period == null) return false;
        if ("TRACKING".equals(period.getStatus())) return before(period.getTrackingEnd());
        return "ACCEPTANCE".equals(period.getStatus()) && before(period.getAcceptanceEnd());
    }

    private boolean before(LocalDateTime deadline) {
        return deadline == null || LocalDateTime.now().isBefore(deadline);
    }

    public List<TodoResponse> adminTodos() {
        List<TodoResponse> result = new ArrayList<>();
        long photos = photoMapper.selectCount(Wrappers.<PairPhoto>lambdaQuery().eq(PairPhoto::getStatus,"ADMIN_PENDING"));
        long reflections = reflectionMapper.selectCount(Wrappers.<PairReflection>lambdaQuery().in(PairReflection::getStatus, List.of("SUBMITTED", "ADMIN_PENDING")));
        long active = pairMapper.selectCount(Wrappers.<SupportPair>lambdaQuery().eq(SupportPair::getStatus,"ACTIVE"));
        if (photos > 0) result.add(new TodoResponse("PHOTO_REVIEW","照片待审核","已完成同伴真实性确认",null,"/admin/materials",photos));
        if (reflections > 0) result.add(new TodoResponse("REFLECTION_SUBMITTED","有新活动感悟","学生已提交活动感悟，请前往查看",null,"/admin/materials",reflections));
        if (active > 0) result.add(new TodoResponse("PAIR_ACCEPTANCE","进行中帮扶对","请在验收阶段人工验收或终止",null,"/admin/materials",active));
        return result;
    }
}
