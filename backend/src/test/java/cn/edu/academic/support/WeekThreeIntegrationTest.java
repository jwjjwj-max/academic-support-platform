package cn.edu.academic.support;

import cn.edu.academic.support.application.mapper.ParticipationApplicationMapper;
import cn.edu.academic.support.application.model.ParticipationApplication;
import cn.edu.academic.support.audit.mapper.OperationLogMapper;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.matching.mapper.SupportPairMapper;
import cn.edu.academic.support.matching.model.SupportPair;
import cn.edu.academic.support.material.dto.*;
import cn.edu.academic.support.material.mapper.*;
import cn.edu.academic.support.material.model.PairPhoto;
import cn.edu.academic.support.material.model.PairReflection;
import cn.edu.academic.support.material.service.MaterialService;
import cn.edu.academic.support.notification.dto.NotificationCreateRequest;
import cn.edu.academic.support.notification.service.NotificationService;
import cn.edu.academic.support.period.dto.AcceptanceWindowExtensionRequest;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.period.service.PeriodService;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.mapper.UserAccountMapper;
import cn.edu.academic.support.user.model.UserAccount;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class WeekThreeIntegrationTest {
    @Autowired MaterialService materialService;
    @Autowired NotificationService notificationService;
    @Autowired UserAccountMapper userMapper;
    @Autowired ActivityPeriodMapper periodMapper;
    @Autowired PeriodService periodService;
    @Autowired ParticipationApplicationMapper applicationMapper;
    @Autowired SupportPairMapper pairMapper;
    @Autowired PairPhotoMapper photoMapper;
    @Autowired PairReflectionMapper reflectionMapper;
    @Autowired PairMaterialHistoryMapper historyMapper;
    @Autowired OperationLogMapper logMapper;
    @Autowired PasswordEncoder encoder;

    @Test
    void photoReflectionAndManualAcceptanceFormAuditedClosedLoop() {
        Scenario s = scenario("closed-loop");
        var firstUpload = materialService.uploadPhoto(actor(s.navigator), s.pair.getId(), jpeg("first.jpg"), "第一次线下答疑", request());
        PairPhoto photo = photoMapper.selectById(firstUpload.photos().getFirst().id());
        assertThat(photo.getStatus()).isEqualTo("PEER_PENDING");

        assertError(() -> materialService.confirmPhoto(actor(s.navigator), photo.getId(), request()), "PHOTO_SELF_CONFIRM");
        materialService.confirmPhoto(actor(s.climber), photo.getId(), request());
        materialService.reviewPhoto(actor(s.admin), photo.getId(), new MaterialReviewRequest("REJECTED", "照片过暗，请重新上传"), request());
        assertThat(photoMapper.selectById(photo.getId()).getStatus()).isEqualTo("REJECTED");

        materialService.resubmitPhoto(actor(s.navigator), photo.getId(), jpeg("second.jpg"), "补充清晰合照", request());
        PairPhoto resubmitted = photoMapper.selectById(photo.getId());
        assertThat(resubmitted.getRevisionNo()).isEqualTo(2);
        assertThat(resubmitted.getRejectionReason()).isNull();
        materialService.confirmPhoto(actor(s.climber), photo.getId(), request());
        materialService.reviewPhoto(actor(s.admin), photo.getId(), new MaterialReviewRequest("APPROVED", null), request());

        String content = "学习成长".repeat(75);
        assertThat(content.codePointCount(0, content.length())).isBetween(300, 500);
        var n = materialService.submitReflection(actor(s.navigator), s.pair.getId(), new ReflectionSubmitRequest(content), request());
        var c = materialService.submitReflection(actor(s.climber), s.pair.getId(), new ReflectionSubmitRequest(content), request());
        assertThat(materialService.mine(s.navigator.getId()).getFirst().reflections())
                .allMatch(item -> item.authorId().equals(s.navigator.getId()));
        assertThat(materialService.mine(s.climber.getId()).getFirst().reflections())
                .allMatch(item -> item.authorId().equals(s.climber.getId()));
        assertThat(materialService.adminList(s.period.getId()).getFirst().reflections()).hasSize(2);

        ActivityPeriod period = periodMapper.selectById(s.period.getId()); period.setStatus("ACCEPTANCE"); periodMapper.updateById(period);
        var accepted = materialService.accept(actor(s.admin), s.pair.getId(), new PairActionRequest("材料真实完整，线下帮扶目标达成"), request());
        assertThat(accepted.status()).isEqualTo("COMPLETED");
        assertThat(historyMapper.selectCount(Wrappers.emptyWrapper())).isEqualTo(8);
        assertThat(logMapper.selectList(Wrappers.emptyWrapper())).extracting("action")
                .contains("PHOTO_SUBMIT", "PHOTO_PEER_CONFIRM", "PHOTO_REVIEW", "PHOTO_RESUBMIT",
                        "REFLECTION_SUBMIT", "PAIR_ACCEPT");
    }

    @Test
    void pairOwnershipAndFileContentAreEnforcedByBackend() {
        Scenario s = scenario("permission");
        UserAccount outsider = user("outsider", "STUDENT", "其他同学");
        assertError(() -> materialService.uploadPhoto(actor(outsider), s.pair.getId(), jpeg("x.jpg"), null, request()), "PAIR_ACCESS_DENIED");
        MockMultipartFile fake = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not-an-image".getBytes());
        assertError(() -> materialService.uploadPhoto(actor(s.navigator), s.pair.getId(), fake, null, request()), "PHOTO_TYPE_INVALID");

        var pair = materialService.uploadPhoto(actor(s.navigator), s.pair.getId(), jpeg("valid.jpg"), null, request());
        Long photoId = pair.photos().getFirst().id();
        assertError(() -> materialService.download(actor(outsider), photoId), "PAIR_ACCESS_DENIED");
        assertThat(materialService.download(actor(s.climber), photoId).resource().exists()).isTrue();
        assertThat(materialService.download(actor(s.admin), photoId).resource().exists()).isTrue();
    }

    @Test
    void submissionsLockAfterSubmitAndAcceptanceNeverCompletesAutomatically() {
        Scenario s = scenario("locked");
        String content = "学习成长".repeat(75);
        PairReflection reflection = reflectionMapper.selectById(materialService.submitReflection(actor(s.navigator), s.pair.getId(),
                new ReflectionSubmitRequest(content), request()).reflections().getFirst().id());
        assertError(() -> materialService.submitReflection(actor(s.navigator), s.pair.getId(), new ReflectionSubmitRequest(content), request()), "REFLECTION_LOCKED");
        assertThat(reflection.getStatus()).isEqualTo("SUBMITTED");
        assertThat(pairMapper.selectById(s.pair.getId()).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void acceptanceWindowAllowsReflectionsAndRejectedPhotoResubmission() {
        Scenario s = scenario("acceptance-submission");
        var uploaded = materialService.uploadPhoto(actor(s.navigator), s.pair.getId(), jpeg("rejected.jpg"), "待修改照片", request());
        Long photoId = uploaded.photos().getFirst().id();
        materialService.confirmPhoto(actor(s.climber), photoId, request());
        materialService.reviewPhoto(actor(s.admin), photoId, new MaterialReviewRequest("REJECTED", "请补充清晰照片"), request());

        ActivityPeriod period = periodMapper.selectById(s.period.getId());
        period.setStatus("ACCEPTANCE");
        periodMapper.updateById(period);

        String content = "学习成长".repeat(75);
        var reflection = materialService.submitReflection(actor(s.navigator), s.pair.getId(),
                new ReflectionSubmitRequest(content), request());
        assertThat(reflection.reflections()).anyMatch(item -> item.authorId().equals(s.navigator.getId())
                && "SUBMITTED".equals(item.status()));

        materialService.resubmitPhoto(actor(s.navigator), photoId, jpeg("resubmitted.jpg"), "验收阶段补交清晰照片", request());
        PairPhoto resubmitted = photoMapper.selectById(photoId);
        assertThat(resubmitted.getRevisionNo()).isEqualTo(2);
        assertThat(resubmitted.getStatus()).isEqualTo("PEER_PENDING");

        var supplemented = materialService.uploadPhoto(actor(s.climber), s.pair.getId(), jpeg("supplement.jpg"), "验收阶段补交过程照片", request());
        assertThat(supplemented.photos()).anyMatch(item -> "supplement.jpg".equals(item.originalFilename())
                && "PEER_PENDING".equals(item.status()));

        var extended = periodService.extendAcceptanceWindow(actor(s.admin), s.period.getId(),
                new AcceptanceWindowExtensionRequest(LocalDateTime.now().plusDays(30), "补充验收阶段材料提交时间"), request());
        assertThat(extended.getAcceptanceEnd()).isAfter(period.getAcceptanceEnd());
    }

    @Test
    void notificationsExpandRecipientsAndExposeUnreadTodo() {
        Scenario s = scenario("notification");
        var sent = notificationService.create(actor(s.admin), new NotificationCreateRequest(s.period.getId(), "材料提交提醒",
                "请在跟踪阶段完成照片和活动感悟。", "PERIOD_PARTICIPANTS"), request());
        assertThat(notificationService.mine(s.navigator.getId())).hasSize(1);
        assertThat(notificationService.unread(s.navigator.getId())).isEqualTo(1);
        assertThat(notificationService.studentTodos(s.navigator.getId())).anyMatch(t -> "NOTIFICATION".equals(t.type()));
        notificationService.read(s.navigator.getId(), sent.getId());
        assertThat(notificationService.unread(s.navigator.getId())).isZero();
    }

    private Scenario scenario(String suffix) {
        UserAccount admin=user("w3-admin-"+suffix,"ADMIN","材料管理员");
        UserAccount navigator=user("w3-nav-"+suffix,"STUDENT","领航同学");
        UserAccount climber=user("w3-climb-"+suffix,"STUDENT","攀登同学");
        ActivityPeriod period=new ActivityPeriod(); period.setName("第三周测试-"+suffix);period.setShortName("W3-"+suffix);period.setStatus("TRACKING");
        LocalDateTime now=LocalDateTime.now();period.setRegistrationStart(now.minusDays(10));period.setRegistrationEnd(now.minusDays(9));
        period.setConfirmationDeadline(now.minusDays(8));period.setTrackingStart(now.minusDays(7));period.setTrackingEnd(now.plusDays(7));
        period.setAcceptanceStart(now.plusDays(8));period.setAcceptanceEnd(now.plusDays(15));period.setPhotoMin(1);period.setPhotoMax(2);
        period.setSubjectWeight(40);period.setMajorWeight(25);period.setGradeWeight(15);period.setLearningStyleWeight(10);period.setPersonalityWeight(5);period.setGpaGapWeight(5);period.setCreatedBy(admin.getId());periodMapper.insert(period);
        ParticipationApplication nav=application(period.getId(),navigator,"NAVIGATOR","4.20"), climb=application(period.getId(),climber,"CLIMBER","3.20");
        SupportPair pair=new SupportPair();pair.setPeriodId(period.getId());pair.setPairNo("PAIR-W3-"+suffix);pair.setNavigatorApplicationId(nav.getId());pair.setClimberApplicationId(climb.getId());pair.setStatus("ACTIVE");pair.setConfirmedAt(now.minusDays(8));pairMapper.insert(pair);
        return new Scenario(admin,navigator,climber,period,pair);
    }
    private ParticipationApplication application(Long periodId,UserAccount user,String role,String gpa){ParticipationApplication a=new ParticipationApplication();a.setPeriodId(periodId);a.setStudentUserId(user.getId());a.setParticipantRole(role);a.setCumulativeGpa(new BigDecimal(gpa));a.setSubjectsJson("[\"高等数学\"]");a.setStatementText("第三周材料测试报名快照");a.setLearningStylesJson("[]");a.setPersonalitiesJson("[]");a.setApplicationStatus("APPROVED");a.setSelectionStatus("MATCHING_POOL");a.setSubmittedAt(LocalDateTime.now());applicationMapper.insert(a);return a;}
    private UserAccount user(String login,String type,String name){UserAccount u=new UserAccount();u.setLoginName(login);u.setPasswordHash(encoder.encode("WeekThree2026"));u.setAccountType(type);u.setDisplayName(name);u.setAccountStatus("ACTIVE");u.setTokenVersion(0);userMapper.insert(u);return u;}
    private AuthenticatedUser actor(UserAccount u){return new AuthenticatedUser(u.getId(),u.getLoginName(),u.getDisplayName(),u.getAccountType(),0);}
    private MockMultipartFile jpeg(String name){
        try {var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"jpg",bytes);return new MockMultipartFile("file",name,"image/jpeg",bytes.toByteArray());}
        catch(java.io.IOException e){throw new RuntimeException(e);}
    }
    private MockHttpServletRequest request(){return new MockHttpServletRequest();}
    private void assertError(Runnable action,String code){BusinessException e=catchThrowableOfType(action::run,BusinessException.class);assertThat(e).isNotNull();assertThat(e.getCode()).isEqualTo(code);}
    private record Scenario(UserAccount admin,UserAccount navigator,UserAccount climber,ActivityPeriod period,SupportPair pair){}
}
