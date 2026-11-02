package cn.edu.academic.support.material.service;

import cn.edu.academic.support.application.mapper.ParticipationApplicationMapper;
import cn.edu.academic.support.application.model.ParticipationApplication;
import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.matching.mapper.SupportPairMapper;
import cn.edu.academic.support.matching.model.SupportPair;
import cn.edu.academic.support.material.dto.*;
import cn.edu.academic.support.material.mapper.*;
import cn.edu.academic.support.material.model.*;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.mapper.UserAccountMapper;
import cn.edu.academic.support.user.model.UserAccount;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MaterialService {
    private final SupportPairMapper pairMapper;
    private final ParticipationApplicationMapper applicationMapper;
    private final ActivityPeriodMapper periodMapper;
    private final PairPhotoMapper photoMapper;
    private final PairReflectionMapper reflectionMapper;
    private final PairMaterialHistoryMapper historyMapper;
    private final UserAccountMapper userMapper;
    private final PrivateFileStorage storage;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public List<PairDetailResponse> mine(Long userId) {
        Set<Long> applicationIds = applicationMapper.selectList(Wrappers.<ParticipationApplication>lambdaQuery()
                        .eq(ParticipationApplication::getStudentUserId, userId)).stream()
                .map(ParticipationApplication::getId).collect(java.util.stream.Collectors.toSet());
        if (applicationIds.isEmpty()) return List.of();
        return pairMapper.selectList(Wrappers.<SupportPair>lambdaQuery()
                        .and(q -> q.in(SupportPair::getNavigatorApplicationId, applicationIds)
                                .or().in(SupportPair::getClimberApplicationId, applicationIds))
                        .orderByDesc(SupportPair::getConfirmedAt)).stream()
                .map(pair -> response(pair, userId, false)).toList();
    }

    public List<PairDetailResponse> adminList(Long periodId) {
        var query = Wrappers.<SupportPair>lambdaQuery().orderByDesc(SupportPair::getConfirmedAt);
        if (periodId != null) query.eq(SupportPair::getPeriodId, periodId);
        return pairMapper.selectList(query).stream().map(pair -> response(pair, null, true)).toList();
    }

    @Transactional
    public PairDetailResponse uploadPhoto(AuthenticatedUser actor, Long pairId, MultipartFile file,
                                           String description, HttpServletRequest request) {
        SupportPair pair = requireStudentPair(pairId, actor.id());
        ActivityPeriod period = requirePhotoSubmission(pair);
        long current = photoMapper.selectCount(Wrappers.<PairPhoto>lambdaQuery().eq(PairPhoto::getPairId, pairId)
                .ne(PairPhoto::getStatus, "REJECTED"));
        if (current >= period.getPhotoMax()) throw new BusinessException("PHOTO_MAX_REACHED", "本期每组最多提交" + period.getPhotoMax() + "张照片");
        PrivateFileStorage.StoredPhoto stored = storage.storePhoto(file);
        PairPhoto photo = new PairPhoto();
        photo.setPairId(pairId); photo.setUploaderId(actor.id()); photo.setStorageKey(stored.key());
        photo.setOriginalFilename(stored.originalFilename()); photo.setMimeType(stored.mimeType()); photo.setFileSize(stored.size());
        photo.setDescription(trim(description, 500)); photo.setStatus("PEER_PENDING"); photo.setRevisionNo(1); photo.setSubmittedAt(LocalDateTime.now());
        photoMapper.insert(photo);
        history(pairId, "PHOTO", photo.getId(), 1, "SUBMIT", null, photo, actor.id());
        auditService.log(actor, "PHOTO_SUBMIT", "PAIR_PHOTO", photo.getId(), null, photo, null, request);
        return response(pair, actor.id(), false);
    }

    @Transactional
    public PairDetailResponse resubmitPhoto(AuthenticatedUser actor, Long photoId, MultipartFile file,
                                            String description, HttpServletRequest request) {
        PairPhoto photo = requiredPhoto(photoId);
        SupportPair pair = requireStudentPair(photo.getPairId(), actor.id());
        requirePhotoResubmission(pair);
        if (!photo.getUploaderId().equals(actor.id()) || !"REJECTED".equals(photo.getStatus()))
            throw new BusinessException("PHOTO_RESUBMIT_FORBIDDEN", "仅上传人可修改被驳回的照片", HttpStatus.FORBIDDEN);
        PairPhoto before = copyPhoto(photo);
        PrivateFileStorage.StoredPhoto stored = storage.storePhoto(file);
        photo.setStorageKey(stored.key()); photo.setOriginalFilename(stored.originalFilename()); photo.setMimeType(stored.mimeType());
        photo.setFileSize(stored.size()); photo.setDescription(trim(description, 500)); photo.setStatus("PEER_PENDING");
        photo.setPeerConfirmedBy(null); photo.setPeerConfirmedAt(null); photo.setReviewedBy(null); photo.setReviewedAt(null);
        photo.setRejectionReason(null); photo.setRevisionNo(photo.getRevisionNo() + 1); photo.setSubmittedAt(LocalDateTime.now());
        photoMapper.updateById(photo);
        history(pair.getId(), "PHOTO", photoId, photo.getRevisionNo(), "RESUBMIT", null, photo, actor.id());
        auditService.log(actor, "PHOTO_RESUBMIT", "PAIR_PHOTO", photoId, before, photo, null, request);
        return response(pair, actor.id(), false);
    }

    @Transactional
    public PairDetailResponse confirmPhoto(AuthenticatedUser actor, Long photoId, HttpServletRequest request) {
        PairPhoto photo = requiredPhoto(photoId);
        SupportPair pair = requireStudentPair(photo.getPairId(), actor.id());
        requireReviewStage(pair);
        if (photo.getUploaderId().equals(actor.id())) throw new BusinessException("PHOTO_SELF_CONFIRM", "不能确认自己上传的照片");
        if (!"PEER_PENDING".equals(photo.getStatus())) throw new BusinessException("PHOTO_NOT_PEER_PENDING", "当前照片无需真实性确认");
        photo.setPeerConfirmedBy(actor.id()); photo.setPeerConfirmedAt(LocalDateTime.now()); photo.setStatus("ADMIN_PENDING");
        photoMapper.updateById(photo);
        history(pair.getId(), "PHOTO", photoId, photo.getRevisionNo(), "PEER_CONFIRM", null, photo, actor.id());
        auditService.log(actor, "PHOTO_PEER_CONFIRM", "PAIR_PHOTO", photoId, null, Map.of("status", "ADMIN_PENDING"), null, request);
        return response(pair, actor.id(), false);
    }

    @Transactional
    public PairDetailResponse reviewPhoto(AuthenticatedUser actor, Long photoId, MaterialReviewRequest decision,
                                          HttpServletRequest request) {
        PairPhoto photo = requiredPhoto(photoId); SupportPair pair = requiredPair(photo.getPairId()); requireReviewStage(pair);
        if (!"ADMIN_PENDING".equals(photo.getStatus())) throw new BusinessException("PHOTO_NOT_ADMIN_PENDING", "照片尚未完成同伴真实性确认或已审核");
        requireReasonIfRejected(decision);
        PairPhoto before = copyPhoto(photo);
        photo.setStatus(decision.decision()); photo.setReviewedBy(actor.id()); photo.setReviewedAt(LocalDateTime.now());
        photo.setRejectionReason("REJECTED".equals(decision.decision()) ? decision.reason().trim() : null);
        photoMapper.updateById(photo);
        history(pair.getId(), "PHOTO", photoId, photo.getRevisionNo(), "APPROVED".equals(decision.decision()) ? "APPROVE" : "REJECT",
                photo.getRejectionReason(), photo, actor.id());
        auditService.log(actor, "PHOTO_REVIEW", "PAIR_PHOTO", photoId, before, photo, photo.getRejectionReason(), request);
        return response(pair, null, true);
    }

    @Transactional
    public PairDetailResponse submitReflection(AuthenticatedUser actor, Long pairId, ReflectionSubmitRequest body,
                                               HttpServletRequest request) {
        SupportPair pair = requireStudentPair(pairId, actor.id()); requireReflectionSubmission(pair);
        String content = body.content().trim(); int count = content.codePointCount(0, content.length());
        if (count < 300 || count > 500) throw new BusinessException("REFLECTION_LENGTH_INVALID", "活动感悟需为300—500字，当前" + count + "字");
        PairReflection existing = reflectionMapper.selectOne(Wrappers.<PairReflection>lambdaQuery()
                .eq(PairReflection::getPairId, pairId).eq(PairReflection::getAuthorId, actor.id()));
        boolean resubmit = existing != null;
        if (resubmit && !"REJECTED".equals(existing.getStatus())) throw new BusinessException("REFLECTION_LOCKED", "感悟提交后不可自行修改，只有被驳回后才能重提");
        PairReflection before = resubmit ? copyReflection(existing) : null;
        PairReflection reflection = resubmit ? existing : new PairReflection();
        reflection.setPairId(pairId); reflection.setAuthorId(actor.id()); reflection.setContent(content); reflection.setWordCount(count);
        reflection.setStatus("SUBMITTED"); reflection.setRejectionReason(null); reflection.setSubmittedAt(LocalDateTime.now());
        reflection.setReviewedBy(null); reflection.setReviewedAt(null);
        if (resubmit) reflectionMapper.updateById(reflection); else reflectionMapper.insert(reflection);
        history(pairId, "REFLECTION", reflection.getId(), resubmit ? 2 : 1, resubmit ? "RESUBMIT" : "SUBMIT", null, reflection, actor.id());
        auditService.log(actor, resubmit ? "REFLECTION_RESUBMIT" : "REFLECTION_SUBMIT", "PAIR_REFLECTION", reflection.getId(), before, reflection, null, request);
        return response(pair, actor.id(), false);
    }

    @Transactional
    public PairDetailResponse accept(AuthenticatedUser actor, Long pairId, PairActionRequest body, HttpServletRequest request) {
        SupportPair pair = requiredPair(pairId); ActivityPeriod period = requiredPeriod(pair.getPeriodId());
        if (!"ACCEPTANCE".equals(period.getStatus()) || !"ACTIVE".equals(pair.getStatus()))
            throw new BusinessException("PAIR_NOT_ACCEPTABLE", "仅验收阶段的进行中帮扶对可以验收");
        long approved = photoMapper.selectCount(Wrappers.<PairPhoto>lambdaQuery().eq(PairPhoto::getPairId, pairId).eq(PairPhoto::getStatus, "APPROVED"));
        long reflections = reflectionMapper.selectCount(Wrappers.<PairReflection>lambdaQuery().eq(PairReflection::getPairId, pairId)
                .in(PairReflection::getStatus, List.of("SUBMITTED", "APPROVED")));
        if (approved < period.getPhotoMin() || approved > period.getPhotoMax() || reflections != 2)
            throw new BusinessException("PAIR_MATERIAL_INCOMPLETE", "验收前需有" + period.getPhotoMin() + "—" + period.getPhotoMax() + "张审核通过照片及双方已提交的活动感悟");
        pair.setStatus("COMPLETED"); pair.setCompletedBy(actor.id()); pair.setCompletedAt(LocalDateTime.now()); pair.setAcceptanceNote(body.reason().trim());
        pairMapper.updateById(pair);
        auditService.log(actor, "PAIR_ACCEPT", "SUPPORT_PAIR", pairId, Map.of("status", "ACTIVE"), Map.of("status", "COMPLETED"), body.reason().trim(), request);
        return response(pair, null, true);
    }

    @Transactional
    public PairDetailResponse end(AuthenticatedUser actor, Long pairId, PairActionRequest body, HttpServletRequest request) {
        SupportPair pair = requiredPair(pairId); ActivityPeriod period = requiredPeriod(pair.getPeriodId());
        if (!List.of("TRACKING", "ACCEPTANCE").contains(period.getStatus()) || !"ACTIVE".equals(pair.getStatus()))
            throw new BusinessException("PAIR_NOT_ENDABLE", "仅跟踪或验收阶段的进行中帮扶对可以解除");
        pair.setStatus("ENDED"); pair.setEndedBy(actor.id()); pair.setEndedAt(LocalDateTime.now()); pair.setEndReason(body.reason().trim());
        pairMapper.updateById(pair);
        auditService.log(actor, "PAIR_END", "SUPPORT_PAIR", pairId, Map.of("status", "ACTIVE"), Map.of("status", "ENDED"), body.reason().trim(), request);
        return response(pair, null, true);
    }

    public void authorizeUpload(AuthenticatedUser actor,Long pairId,Long photoId) {
        if(!"STUDENT".equals(actor.accountType())) throw new BusinessException("FORBIDDEN","仅学生可提交照片",HttpStatus.FORBIDDEN);
        if(photoId!=null) {
            PairPhoto photo=requiredPhoto(photoId); SupportPair pair=requireStudentPair(photo.getPairId(),actor.id());
            requirePhotoResubmission(pair);
            if(!photo.getUploaderId().equals(actor.id()) || !"REJECTED".equals(photo.getStatus()))
                throw new BusinessException("PHOTO_RESUBMIT_FORBIDDEN","仅上传人可修改被驳回的照片",HttpStatus.FORBIDDEN);
        } else {
            SupportPair pair=requireStudentPair(pairId,actor.id()); ActivityPeriod period=requirePhotoSubmission(pair);
            long count=photoMapper.selectCount(Wrappers.<PairPhoto>lambdaQuery().eq(PairPhoto::getPairId,pairId).ne(PairPhoto::getStatus,"REJECTED"));
            if(count>=period.getPhotoMax()) throw new BusinessException("PHOTO_MAX_REACHED","照片数量已达上限");
        }
    }
    public String accessUrl(AuthenticatedUser actor, Long photoId) {
        PairPhoto photo=requiredPhoto(photoId);
        if (!"ADMIN".equals(actor.accountType())) requireStudentPair(photo.getPairId(),actor.id());
        String url=storage.signedGet(photo.getStorageKey());
        return url==null ? "/api/materials/photos/"+photoId+"/file" : url;
    }

    public DownloadedPhoto download(AuthenticatedUser actor, Long photoId) {
        PairPhoto photo = requiredPhoto(photoId); SupportPair pair = requiredPair(photo.getPairId());
        if (!"ADMIN".equals(actor.accountType())) requireStudentPair(pair.getId(), actor.id());
        Resource resource = storage.resource(photo.getStorageKey());
        return new DownloadedPhoto(resource, photo.getOriginalFilename(), photo.getMimeType(), photo.getFileSize());
    }

    private PairDetailResponse response(SupportPair pair, Long viewerId, boolean admin) {
        ActivityPeriod period = requiredPeriod(pair.getPeriodId());
        ParticipationApplication nav = applicationMapper.selectById(pair.getNavigatorApplicationId());
        ParticipationApplication climb = applicationMapper.selectById(pair.getClimberApplicationId());
        UserAccount navUser = userMapper.selectById(nav.getStudentUserId()), climbUser = userMapper.selectById(climb.getStudentUserId());
        List<PairDetailResponse.Photo> photos = photoMapper.selectList(Wrappers.<PairPhoto>lambdaQuery().eq(PairPhoto::getPairId, pair.getId())
                .orderByAsc(PairPhoto::getSubmittedAt)).stream().map(p -> new PairDetailResponse.Photo(p.getId(), p.getUploaderId(),
                userName(p.getUploaderId()), p.getOriginalFilename(), p.getMimeType(), p.getFileSize(), p.getDescription(), p.getStatus(), p.getRevisionNo(),
                p.getSubmittedAt(), p.getPeerConfirmedAt(), p.getReviewedAt(), p.getRejectionReason(),
                !admin && viewerId != null && "ACTIVE".equals(pair.getStatus()) && !p.getUploaderId().equals(viewerId)
                        && List.of("TRACKING", "ACCEPTANCE").contains(period.getStatus()) && "PEER_PENDING".equals(p.getStatus()),
                !admin && viewerId != null && "ACTIVE".equals(pair.getStatus()) && p.getUploaderId().equals(viewerId)
                        && "REJECTED".equals(p.getStatus()) && canResubmitInCurrentWindow(period),
                "/api/materials/photos/" + p.getId() + "/file")).toList();
        var reflectionQuery = Wrappers.<PairReflection>lambdaQuery().eq(PairReflection::getPairId, pair.getId());
        if (!admin && viewerId != null) reflectionQuery.eq(PairReflection::getAuthorId, viewerId);
        List<PairDetailResponse.Reflection> reflections = reflectionMapper.selectList(reflectionQuery.orderByAsc(PairReflection::getSubmittedAt)).stream()
                .map(r -> new PairDetailResponse.Reflection(r.getId(), r.getAuthorId(), userName(r.getAuthorId()), r.getContent(), r.getWordCount(),
                        r.getStatus(), r.getSubmittedAt(), r.getReviewedAt(), r.getRejectionReason(),
                        !admin && viewerId != null && "ACTIVE".equals(pair.getStatus()) && r.getAuthorId().equals(viewerId)
                                && "REJECTED".equals(r.getStatus()) && canReflectionSubmitInCurrentPhase(period))).toList();
        boolean canUploadPhoto = !admin && "ACTIVE".equals(pair.getStatus()) && canPhotoUploadInCurrentPhase(period);
        boolean canSubmitReflection = !admin && "ACTIVE".equals(pair.getStatus()) && canReflectionSubmitInCurrentPhase(period);
        boolean canSubmit = canUploadPhoto || canSubmitReflection;
        boolean canAccept = admin && "ACTIVE".equals(pair.getStatus()) && "ACCEPTANCE".equals(period.getStatus());
        return new PairDetailResponse(pair.getId(), pair.getPeriodId(), period.getName(), period.getStatus(), pair.getPairNo(), pair.getStatus(), pair.getConfirmedAt(),
                pair.getCompletedAt(), pair.getAcceptanceNote(), pair.getEndedAt(), pair.getEndReason(), period.getPhotoMin(), period.getPhotoMax(),
                new PairDetailResponse.Member(navUser.getId(), navUser.getDisplayName(), "NAVIGATOR"),
                new PairDetailResponse.Member(climbUser.getId(), climbUser.getDisplayName(), "CLIMBER"), photos, reflections,
                canSubmit, canUploadPhoto, canSubmitReflection, canAccept);
    }

    private SupportPair requireStudentPair(Long pairId, Long userId) {
        SupportPair pair = requiredPair(pairId); ParticipationApplication n = applicationMapper.selectById(pair.getNavigatorApplicationId());
        ParticipationApplication c = applicationMapper.selectById(pair.getClimberApplicationId());
        if (!n.getStudentUserId().equals(userId) && !c.getStudentUserId().equals(userId))
            throw new BusinessException("PAIR_ACCESS_DENIED", "无权访问其他帮扶对的材料", HttpStatus.FORBIDDEN);
        return pair;
    }
    private ActivityPeriod requirePhotoSubmission(SupportPair pair) {
        ActivityPeriod p = requiredPeriod(pair.getPeriodId());
        if (!List.of("TRACKING", "ACCEPTANCE").contains(p.getStatus()) || !"ACTIVE".equals(pair.getStatus()))
            throw new BusinessException("PHOTO_SUBMISSION_NOT_OPEN", "仅帮扶开展或验收阶段可以提交过程照片");
        if (!isBefore(materialSubmissionDeadline(p)))
            throw new BusinessException("MATERIAL_SUBMISSION_CLOSED", "过程照片提交已截止，请联系管理员处理延期");
        return p;
    }

    private ActivityPeriod requirePhotoResubmission(SupportPair pair) {
        ActivityPeriod p = requiredPeriod(pair.getPeriodId());
        if (!List.of("TRACKING", "ACCEPTANCE").contains(p.getStatus()) || !"ACTIVE".equals(pair.getStatus()))
            throw new BusinessException("MATERIAL_RESUBMISSION_NOT_OPEN", "仅帮扶开展或验收阶段可以重提被驳回材料");
        if (!canResubmitInCurrentWindow(p))
            throw new BusinessException("MATERIAL_SUBMISSION_CLOSED", "材料提交已截止，请联系管理员处理延期");
        return p;
    }

    private ActivityPeriod requireReflectionSubmission(SupportPair pair) {
        ActivityPeriod p = requiredPeriod(pair.getPeriodId());
        if (!List.of("TRACKING", "ACCEPTANCE").contains(p.getStatus()) || !"ACTIVE".equals(pair.getStatus()))
            throw new BusinessException("REFLECTION_SUBMISSION_NOT_OPEN", "仅帮扶开展或验收阶段可以提交活动感悟");
        if (!canReflectionSubmitInCurrentPhase(p))
            throw new BusinessException("REFLECTION_SUBMISSION_CLOSED", "活动感悟提交已截止，请联系管理员延长验收提交窗口");
        return p;
    }

    private boolean canPhotoUploadInCurrentPhase(ActivityPeriod period) {
        return List.of("TRACKING", "ACCEPTANCE").contains(period.getStatus()) && isBefore(materialSubmissionDeadline(period));
    }

    private boolean canReflectionSubmitInCurrentPhase(ActivityPeriod period) {
        if ("TRACKING".equals(period.getStatus())) return isBefore(period.getTrackingEnd());
        return "ACCEPTANCE".equals(period.getStatus()) && isBefore(period.getAcceptanceEnd());
    }

    private boolean canResubmitInCurrentWindow(ActivityPeriod period) {
        return canReflectionSubmitInCurrentPhase(period);
    }

    private LocalDateTime materialSubmissionDeadline(ActivityPeriod period) {
        return "ACCEPTANCE".equals(period.getStatus()) ? period.getAcceptanceEnd() : period.getTrackingEnd();
    }

    private boolean isBefore(LocalDateTime deadline) {
        return deadline == null || LocalDateTime.now().isBefore(deadline);
    }
    private ActivityPeriod requireReviewStage(SupportPair pair) {
        ActivityPeriod p = requiredPeriod(pair.getPeriodId());
        if (!List.of("TRACKING", "ACCEPTANCE").contains(p.getStatus()))
            throw new BusinessException("MATERIAL_REVIEW_NOT_OPEN", "仅跟踪或验收阶段可以处理材料");
        return p;
    }
    private SupportPair requiredPair(Long id) { SupportPair p = pairMapper.selectById(id); if (p == null) throw new BusinessException("PAIR_NOT_FOUND", "帮扶对不存在", HttpStatus.NOT_FOUND); return p; }
    private ActivityPeriod requiredPeriod(Long id) { ActivityPeriod p = periodMapper.selectById(id); if (p == null) throw new BusinessException("PERIOD_NOT_FOUND", "期次不存在", HttpStatus.NOT_FOUND); return p; }
    private PairPhoto requiredPhoto(Long id) { PairPhoto p = photoMapper.selectById(id); if (p == null) throw new BusinessException("PHOTO_NOT_FOUND", "照片不存在", HttpStatus.NOT_FOUND); return p; }
    private void requireReasonIfRejected(MaterialReviewRequest r) { if ("REJECTED".equals(r.decision()) && (r.reason() == null || r.reason().isBlank())) throw new BusinessException("REJECTION_REASON_REQUIRED", "驳回必须填写原因"); }
    private String userName(Long id) { UserAccount u = userMapper.selectById(id); return u == null ? "未知用户" : u.getDisplayName(); }
    private String trim(String value, int max) { if (value == null) return null; String v=value.trim(); return v.length() <= max ? v : v.substring(0,max); }
    private void history(Long pairId, String type, Long id, int revision, String action, String reason, Object snapshot, Long operator) {
        PairMaterialHistory h = new PairMaterialHistory(); h.setPairId(pairId); h.setMaterialType(type); h.setMaterialId(id); h.setRevisionNo(revision);
        h.setAction(action); h.setReason(reason); h.setSnapshot(json(snapshot)); h.setOperatorId(operator); historyMapper.insert(h);
    }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { return "{}"; } }
    private PairPhoto copyPhoto(PairPhoto p) { return objectMapper.convertValue(p, PairPhoto.class); }
    private PairReflection copyReflection(PairReflection p) { return objectMapper.convertValue(p, PairReflection.class); }
    public record DownloadedPhoto(Resource resource, String filename, String mimeType, long size) {}
}
