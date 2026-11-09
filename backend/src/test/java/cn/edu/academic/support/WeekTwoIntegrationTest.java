package cn.edu.academic.support;

import cn.edu.academic.support.application.dto.ApplicationCorrectionRequest;
import cn.edu.academic.support.application.dto.ApplicationResponse;
import cn.edu.academic.support.application.dto.ApplicationReviewRequest;
import cn.edu.academic.support.application.dto.ApplicationRoleCorrectionRequest;
import cn.edu.academic.support.application.dto.ApplicationSelectionRequest;
import cn.edu.academic.support.application.dto.ApplicationSubmitRequest;
import cn.edu.academic.support.application.mapper.ApplicationProfileSnapshotMapper;
import cn.edu.academic.support.application.mapper.ParticipationApplicationMapper;
import cn.edu.academic.support.application.model.ApplicationProfileSnapshot;
import cn.edu.academic.support.application.model.ParticipationApplication;
import cn.edu.academic.support.application.service.ApplicationService;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.matching.dto.MatchCandidateResponse;
import cn.edu.academic.support.matching.dto.MatchDecisionRequest;
import cn.edu.academic.support.matching.dto.MatchingEvidence;
import cn.edu.academic.support.matching.dto.MatchingPolicy;
import cn.edu.academic.support.matching.mapper.MatchCandidateMapper;
import cn.edu.academic.support.matching.mapper.SupportPairMapper;
import cn.edu.academic.support.matching.model.MatchCandidate;
import cn.edu.academic.support.matching.service.MatchingService;
import cn.edu.academic.support.matching.service.MatchingWorkspace;
import cn.edu.academic.support.matching.service.DeepSeekMatchingClient;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.dto.PeriodStatusRequest;
import cn.edu.academic.support.period.dto.PeriodTransitionPreview;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.period.service.PeriodService;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.mapper.StudentProfileMapper;
import cn.edu.academic.support.user.mapper.UserAccountMapper;
import cn.edu.academic.support.user.model.StudentProfile;
import cn.edu.academic.support.user.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "MATCHING_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@ActiveProfiles("test")
@Transactional
class WeekTwoIntegrationTest {
    @Autowired MatchingService matchingService;
    @Autowired MatchingWorkspace workspace;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean DeepSeekMatchingClient deepSeek;
    @Autowired ApplicationService applicationService;
    @Autowired PeriodService periodService;
    @Autowired UserAccountMapper userMapper;
    @Autowired StudentProfileMapper profileMapper;
    @Autowired ParticipationApplicationMapper applicationMapper;
    @Autowired ApplicationProfileSnapshotMapper snapshotMapper;
    @Autowired ActivityPeriodMapper periodMapper;
    @Autowired MatchCandidateMapper candidateMapper;
    @Autowired SupportPairMapper supportPairMapper;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void registrationAllowsFirstSubmitButBlocksReviewAndSelection() {
        AuthenticatedUser admin = actor(createUser("stage-registration-admin", "ADMIN", "报名阶段管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "REGISTRATION", "报名阶段权限测试");
        UserAccount student = createApprovedStudent("stage-registration-student", "报名学生", 2024);

        ApplicationResponse submitted = applicationService.submit(actor(student), submission(period.getId(), "CLIMBER", "3.20"), request());
        assertThat(submitted.applicationStatus()).isEqualTo("SUBMITTED");

        assertBusinessError(() -> applicationService.review(admin, submitted.id(),
                new ApplicationReviewRequest("APPROVED", null), request()), "APPLICATION_REVIEW_NOT_OPEN");
        assertBusinessError(() -> applicationService.select(admin, submitted.id(),
                new ApplicationSelectionRequest("MATCHING_POOL"), request()), "APPLICATION_SELECTION_NOT_OPEN");
        assertThat(applicationMapper.selectById(submitted.id()).getApplicationStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void registrationRequiresBothRegistrationStageAndOpenTimeWindow() {
        AuthenticatedUser admin = actor(createUser("registration-window-admin", "ADMIN", "报名窗口管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "REGISTRATION", "报名时间硬限制测试");
        UserAccount student = createApprovedStudent("registration-window-student", "窗口测试学生", 2024);

        period.setRegistrationStart(LocalDateTime.now().plusHours(1));
        period.setRegistrationEnd(LocalDateTime.now().plusHours(2));
        periodMapper.updateById(period);
        assertBusinessError(() -> applicationService.submit(actor(student),
                submission(period.getId(), "CLIMBER", "3.20"), request()), "REGISTRATION_NOT_STARTED");

        period.setRegistrationStart(LocalDateTime.now().minusHours(2));
        period.setRegistrationEnd(LocalDateTime.now().minusHours(1));
        periodMapper.updateById(period);
        assertBusinessError(() -> applicationService.submit(actor(student),
                submission(period.getId(), "CLIMBER", "3.20"), request()), "REGISTRATION_ENDED");
    }

    @Test
    void earlyRegistrationCloseRequiresExplicitRiskConfirmation() {
        AuthenticatedUser admin = actor(createUser("early-transition-admin", "ADMIN", "提前推进管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "REGISTRATION", "提前推进二次确认测试");
        period.setRegistrationEnd(LocalDateTime.now().plusDays(2));
        periodMapper.updateById(period);

        PeriodTransitionPreview preview = periodService.transitionPreview(period.getId());
        assertThat(preview.allowed()).isTrue();
        assertThat(preview.requiresConfirmation()).isTrue();
        assertThat(preview.warnings()).anyMatch(message -> message.contains("提前关闭"));
        assertBusinessError(() -> periodService.changeStatus(admin, period.getId(),
                new PeriodStatusRequest("REVIEW", "提前结束报名", false), request()),
                "PERIOD_TRANSITION_CONFIRMATION_REQUIRED");

        ActivityPeriod advanced = periodService.changeStatus(admin, period.getId(),
                new PeriodStatusRequest("REVIEW", "报名人数已满足，提前结束报名", true), request());
        assertThat(advanced.getStatus()).isEqualTo("REVIEW");
    }

    @Test
    void reviewCannotAdvanceWhileApplicationsRemainPendingOrApprovedUndecided() {
        AuthenticatedUser admin = actor(createUser("review-transition-admin", "ADMIN", "审核推进管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "REVIEW", "审核阶段前置校验测试");
        ParticipationApplication pending = createParticipant(period.getId(), "transition-pending", "待审核报名",
                "CLIMBER", 2024, new BigDecimal("3.00"), "高等数学", "transition-pending",
                "SUBMITTED", "UNDECIDED");
        ParticipationApplication approved = createParticipant(period.getId(), "transition-approved", "待定入选报名",
                "NAVIGATOR", 2023, new BigDecimal("4.00"), "高等数学", "transition-approved",
                "APPROVED", "UNDECIDED");

        PeriodTransitionPreview blocked = periodService.transitionPreview(period.getId());
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.blockers()).hasSize(2);
        assertBusinessError(() -> periodService.changeStatus(admin, period.getId(),
                new PeriodStatusRequest("MATCHING", "开始匹配", false), request()), "PERIOD_TRANSITION_BLOCKED");

        pending.setApplicationStatus("REJECTED");
        applicationMapper.updateById(pending);
        approved.setSelectionStatus("WAITLIST");
        applicationMapper.updateById(approved);
        assertThat(periodService.transitionPreview(period.getId()).allowed()).isTrue();
    }

    @Test
    void reviewStageBlocksFirstSubmitButAllowsReviewAndApprovedSelection() {
        AuthenticatedUser admin = actor(createUser("stage-review-admin", "ADMIN", "审核阶段管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "REVIEW", "审核阶段权限测试");
        UserAccount newStudent = createApprovedStudent("stage-review-new", "未报名学生", 2024);
        assertBusinessError(() -> applicationService.submit(actor(newStudent),
                submission(period.getId(), "CLIMBER", "3.10"), request()), "REGISTRATION_NOT_OPEN");

        ParticipationApplication application = createParticipant(period.getId(), "stage-review-existing", "待审核学生",
                "CLIMBER", 2024, new BigDecimal("3.20"), "高等数学", "review-wechat",
                "SUBMITTED", "UNDECIDED");
        ApplicationResponse approved = applicationService.review(admin, application.getId(),
                new ApplicationReviewRequest("APPROVED", null), request());
        assertThat(approved.applicationStatus()).isEqualTo("APPROVED");

        ApplicationResponse selected = applicationService.select(admin, application.getId(),
                new ApplicationSelectionRequest("MATCHING_POOL"), request());
        assertThat(selected.selectionStatus()).isEqualTo("MATCHING_POOL");

        ApplicationResponse withdrawn = applicationService.select(admin, application.getId(),
                new ApplicationSelectionRequest("REMOVED_FROM_POOL"), request());
        assertThat(withdrawn.selectionStatus()).isEqualTo("REMOVED_FROM_POOL");

        ApplicationResponse restored = applicationService.select(admin, application.getId(),
                new ApplicationSelectionRequest("MATCHING_POOL"), request());
        assertThat(restored.selectionStatus()).isEqualTo("MATCHING_POOL");
    }

    @Test
    void rejectedStudentCanResubmitDuringReviewStage() {
        AuthenticatedUser admin = actor(createUser("stage-resubmit-admin", "ADMIN", "重提审核管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "REVIEW", "驳回重提权限测试");
        ParticipationApplication application = createParticipant(period.getId(), "stage-resubmit-student", "重提学生",
                "CLIMBER", 2024, new BigDecimal("3.05"), "大学英语", "resubmit-wechat",
                "SUBMITTED", "UNDECIDED");

        ApplicationResponse rejected = applicationService.review(admin, application.getId(),
                new ApplicationReviewRequest("REJECTED", "报名自述需要补充具体帮扶目标"), request());
        assertThat(rejected.applicationStatus()).isEqualTo("REJECTED");

        var correction = new ApplicationCorrectionRequest(rejected.revision(), 1, 2025, "人工智能",
                null, null, null, "与学生核对年级专业", false);
        assertThat(applicationService.correct(admin, application.getId(), correction, request()).applicationStatus())
                .isEqualTo("REJECTED");
        AuthenticatedUser student = actor(userMapper.selectById(application.getStudentUserId()));
        ApplicationResponse resubmitted = applicationService.submit(student,
                submission(period.getId(), "CLIMBER", "3.05"), request());
        assertThat(resubmitted.id()).isEqualTo(application.getId());
        assertThat(resubmitted.applicationStatus()).isEqualTo("SUBMITTED");
        assertThat(resubmitted.selectionStatus()).isEqualTo("UNDECIDED");
        assertThat(resubmitted.rejectionReason()).isNull();
        assertThat(resubmitted.grade()).isEqualTo(2025);
        assertThat(resubmitted.major()).isEqualTo("人工智能");
    }

    @Test
    void matchingStageBlocksReviewAndSelection() {
        AuthenticatedUser admin = actor(createUser("stage-matching-admin", "ADMIN", "匹配阶段管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "MATCHING", "匹配阶段权限测试");
        ParticipationApplication submitted = createParticipant(period.getId(), "stage-matching-submitted", "匹配期待审核",
                "CLIMBER", 2024, new BigDecimal("3.00"), "高等数学", "matching-submitted",
                "SUBMITTED", "UNDECIDED");
        ParticipationApplication approved = createParticipant(period.getId(), "stage-matching-approved", "匹配期已通过",
                "NAVIGATOR", 2023, new BigDecimal("4.00"), "高等数学", "matching-approved",
                "APPROVED", "MATCHING_POOL");

        assertBusinessError(() -> applicationService.review(admin, submitted.getId(),
                new ApplicationReviewRequest("APPROVED", null), request()), "APPLICATION_REVIEW_NOT_OPEN");
        assertBusinessError(() -> applicationService.select(admin, approved.getId(),
                new ApplicationSelectionRequest("WAITLIST"), request()), "APPLICATION_SELECTION_NOT_OPEN");
    }

    @Test
    void matchingUsesOnlyApprovedApplicationsInMatchingPoolAndOnlyMatchingStageCanStart() {
        AuthenticatedUser admin = actor(createUser("matching-filter-admin", "ADMIN", "匹配过滤管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "MATCHING", "匹配池过滤测试");
        ParticipationApplication eligibleNavigator = createParticipant(period.getId(), "filter-nav-ok", "合格领航者",
                "NAVIGATOR", 2022, new BigDecimal("4.20"), "线性代数", "filter-nav-ok",
                "APPROVED", "MATCHING_POOL");
        ParticipationApplication eligibleClimber = createParticipant(period.getId(), "filter-cli-ok", "合格攀登者",
                "CLIMBER", 2023, new BigDecimal("3.10"), "线代", "filter-cli-ok",
                "APPROVED", "MATCHING_POOL");
        createParticipant(period.getId(), "filter-nav-unapproved", "未通过领航者", "NAVIGATOR", 2022,
                new BigDecimal("4.30"), "线性代数", "filter-nav-unapproved", "SUBMITTED", "MATCHING_POOL");
        createParticipant(period.getId(), "filter-nav-waitlist", "候补领航者", "NAVIGATOR", 2022,
                new BigDecimal("4.10"), "线性代数", "filter-nav-waitlist", "APPROVED", "WAITLIST");
        createParticipant(period.getId(), "filter-nav-removed", "已撤出领航者", "NAVIGATOR", 2022,
                new BigDecimal("4.15"), "线性代数", "filter-nav-removed", "APPROVED", "REMOVED_FROM_POOL");
        createParticipant(period.getId(), "filter-cli-rejected", "已驳回攀登者", "CLIMBER", 2023,
                new BigDecimal("3.00"), "线性代数", "filter-cli-rejected", "REJECTED", "MATCHING_POOL");
        createParticipant(period.getId(), "filter-cli-not-selected", "未入选攀登者", "CLIMBER", 2023,
                new BigDecimal("3.00"), "线性代数", "filter-cli-not-selected", "APPROVED", "NOT_SELECTED");

        List<MatchCandidateResponse> candidates = matchingService.generate(admin, period.getId(), request());
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().navigatorApplicationId()).isEqualTo(eligibleNavigator.getId());
        assertThat(candidates.getFirst().climberApplicationId()).isEqualTo(eligibleClimber.getId());

        period.setStatus("CONFIRMATION");
        periodMapper.updateById(period);
        assertBusinessError(() -> matchingService.generate(admin, period.getId(), request()), "PERIOD_NOT_MATCHING");
    }

    @Test
    void tenPairsCompleteMatchingAndContactsOpenOnlyAfterBothConfirm() {
        AuthenticatedUser admin = actor(createUser("week2-admin", "ADMIN", "第二周管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "REGISTRATION", "十组完整匹配测试");
        for (int index = 0; index < 10; index++) {
            UserAccount navigator = createApprovedStudent("week2-nav-" + index, "领航者" + index, 2022);
            UserAccount climber = createApprovedStudent("week2-cli-" + index, "攀登者" + index, 2023);
            applicationService.submit(actor(navigator), submission(period.getId(), "NAVIGATOR", "4.00"), request());
            applicationService.submit(actor(climber), submission(period.getId(), "CLIMBER", "3.00"), request());
        }

        period.setRegistrationEnd(LocalDateTime.now().minusSeconds(1));
        periodMapper.updateById(period);
        periodService.changeStatus(admin, period.getId(),
                new PeriodStatusRequest("REVIEW", "报名截止，进入人工审核", false), request());
        List<ParticipationApplication> applications = applicationMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ParticipationApplication>lambdaQuery()
                        .eq(ParticipationApplication::getPeriodId, period.getId()));
        for (ParticipationApplication application : applications) {
            applicationService.review(admin, application.getId(), new ApplicationReviewRequest("APPROVED", null), request());
            applicationService.select(admin, application.getId(), new ApplicationSelectionRequest("MATCHING_POOL"), request());
        }
        periodService.changeStatus(admin, period.getId(),
                new PeriodStatusRequest("MATCHING", "审核和入选已完成", false), request());

        List<MatchCandidateResponse> candidates = matchingService.generate(admin, period.getId(), request());
        MatchCandidateResponse manualChoice = candidates.stream().filter(item -> "GENERATED".equals(item.status())).findFirst().orElseThrow();
        matchingService.select(admin, manualChoice.id(), request());
        assertThat(candidateMapper.selectById(manualChoice.id()).getStatus()).isEqualTo("SELECTED");
        List<MatchCandidateResponse> afterFirstAdjustment = matchingService.listAdmin(period.getId(), manualChoice.roundId());
        Set<Long> usedNavigators = afterFirstAdjustment.stream().filter(item -> "SELECTED".equals(item.status()))
                .map(MatchCandidateResponse::navigatorApplicationId).collect(java.util.stream.Collectors.toSet());
        Set<Long> usedClimbers = afterFirstAdjustment.stream().filter(item -> "SELECTED".equals(item.status()))
                .map(MatchCandidateResponse::climberApplicationId).collect(java.util.stream.Collectors.toSet());
        MatchCandidateResponse completingChoice = afterFirstAdjustment.stream()
                .filter(item -> "GENERATED".equals(item.status()))
                .filter(item -> !usedNavigators.contains(item.navigatorApplicationId()))
                .filter(item -> !usedClimbers.contains(item.climberApplicationId()))
                .findFirst().orElseThrow();
        matchingService.select(admin, completingChoice.id(), request());
        List<MatchCandidateResponse> selected = matchingService.listAdmin(period.getId(), manualChoice.roundId()).stream()
                .filter(item -> "SELECTED".equals(item.status())).toList();
        assertThat(selected).hasSize(10);
        assertThat(new HashSet<>(selected.stream().map(MatchCandidateResponse::navigatorApplicationId).toList())).hasSize(10);
        assertThat(new HashSet<>(selected.stream().map(MatchCandidateResponse::climberApplicationId).toList())).hasSize(10);
        assertThat(candidates).allSatisfy(item -> {
            assertThat(item.navigatorGrade()).isLessThanOrEqualTo(item.climberGrade());
            assertThat(item.matchedSubjects()).isNotEmpty();
            assertThat(item.scoreDetail()).containsKeys("subject", "major", "grade", "learningStyle", "personality", "gpaGap");
        });

        long roundId = selected.getFirst().roundId();
        List<MatchCandidateResponse> published = matchingService.publish(admin, roundId, request()).stream()
                .filter(item -> "PUBLISHED".equals(item.status())).toList();
        assertThat(matchingService.mine(published.getFirst().navigatorUserId())).isEmpty();
        periodService.changeStatus(admin, period.getId(),
                new PeriodStatusRequest("CONFIRMATION", "匹配结果已人工确认并发布", false), request());
        List<MatchCandidateResponse> studentMatches = matchingService.mine(published.getFirst().navigatorUserId());
        assertThat(studentMatches).hasSize(1);
        assertThat(studentMatches.getFirst().totalScore()).isNull();
        assertThat(studentMatches.getFirst().scoreDetail()).isNull();

        for (int index = 0; index < published.size(); index++) {
            MatchCandidateResponse item = published.get(index);
            AuthenticatedUser navigator = actor(userMapper.selectById(item.navigatorUserId()));
            AuthenticatedUser climber = actor(userMapper.selectById(item.climberUserId()));
            MatchCandidateResponse oneConfirmed = matchingService.decide(navigator, item.id(),
                    new MatchDecisionRequest("CONFIRMED", null), request());
            assertThat(oneConfirmed.partnerWechat()).isNull();
            MatchCandidateResponse formal = matchingService.decide(climber, item.id(),
                    new MatchDecisionRequest("CONFIRMED", null), request());
            assertThat(formal.status()).isEqualTo("CONFIRMED");
            assertThat(formal.supportPairId()).isNotNull();
            assertThat(formal.partnerWechat()).isNotBlank();
        }
        assertThat(supportPairMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery())).isEqualTo(10);
    }

    @Test
    void rejectionOnlyRematchesAffectedStudentsAndRejectedCombinationIsExcluded() {
        AuthenticatedUser admin = actor(createUser("week2-reject-admin", "ADMIN", "拒绝流程管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "MATCHING", "拒绝后局部重匹配测试");
        createParticipant(period.getId(), "week2-re-nav-1", "领航甲", "NAVIGATOR", 2022, new BigDecimal("4.20"), "线性代数", "nav-a");
        createParticipant(period.getId(), "week2-re-nav-2", "领航乙", "NAVIGATOR", 2022, new BigDecimal("4.10"), "线代", "nav-b");
        createParticipant(period.getId(), "week2-re-nav-3", "领航丙", "NAVIGATOR", 2022, new BigDecimal("4.00"), "线代", "nav-c");
        createParticipant(period.getId(), "week2-re-nav-4", "领航丁", "NAVIGATOR", 2022, new BigDecimal("3.90"), "线代", "nav-d");
        createParticipant(period.getId(), "week2-re-cli-1", "攀登甲", "CLIMBER", 2023, new BigDecimal("3.00"), "线性代数", "cli-a");
        createParticipant(period.getId(), "week2-re-cli-2", "攀登乙", "CLIMBER", 2023, new BigDecimal("3.10"), "线性代数", "cli-b");

        List<MatchCandidateResponse> first = matchingService.generate(admin, period.getId(), request());
        MatchCandidateResponse rejectedCandidate = first.stream().filter(item -> "SELECTED".equals(item.status())).findFirst().orElseThrow();
        matchingService.publish(admin, rejectedCandidate.roundId(), request());
        period.setStatus("CONFIRMATION");
        periodMapper.updateById(period);
        ParticipationApplication rejectedNavigatorApplication = applicationMapper.selectById(rejectedCandidate.navigatorApplicationId());
        AuthenticatedUser rejectingStudent = actor(userMapper.selectById(rejectedNavigatorApplication.getStudentUserId()));
        matchingService.decide(rejectingStudent, rejectedCandidate.id(), new MatchDecisionRequest("REJECTED", "学习方式不合适"), request());

        assertBusinessError(() -> matchingService.generate(admin, period.getId(), request()), "PERIOD_NOT_MATCHING");
        MatchCandidateResponse staleNormalCandidate = first.stream().filter(item -> "GENERATED".equals(item.status())).findFirst().orElseThrow();
        assertBusinessError(() -> matchingService.select(admin, staleNormalCandidate.id(), request()),
                "MATCHING_ROUND_OPERATION_NOT_OPEN");
        assertBusinessError(() -> matchingService.publish(admin, rejectedCandidate.roundId(), request()),
                "MATCHING_ROUND_OPERATION_NOT_OPEN");

        List<MatchCandidateResponse> second = matchingService.rematch(admin, period.getId(), request());
        assertThat(second).allSatisfy(candidate -> assertThat(candidate.roundType()).isEqualTo("LOCAL_REMATCH"));
        assertThat(second).noneSatisfy(candidate -> assertThat(
                candidate.navigatorApplicationId().equals(rejectedCandidate.navigatorApplicationId())
                        && candidate.climberApplicationId().equals(rejectedCandidate.climberApplicationId())).isTrue());
        MatchCandidateResponse localAlternative = second.stream().filter(item -> "GENERATED".equals(item.status())).findFirst().orElseThrow();
        matchingService.select(admin, localAlternative.id(), request());
        matchingService.publish(admin, localAlternative.roundId(), request());
        assertBusinessError(() -> matchingService.rematch(admin, period.getId(), request()), "NO_PENDING_REJECTION");
        assertThat(candidateMapper.selectById(rejectedCandidate.id()).getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void administratorRoleCorrectionChangesSupplyAndInvalidatesTheDraftBeforePublication() {
        AuthenticatedUser admin = actor(createUser("role-admin", "ADMIN", "身份更正管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "MATCHING", "身份更正测试");
        long periodId = period.getId();
        var higher = createParticipant(periodId, "role-high", "高绩点学生", "CLIMBER", 2025, new BigDecimal("4.30"), "高等数学", "fixture");
        var lower = createParticipant(periodId, "role-low", "待帮扶学生", "CLIMBER", 2025, new BigDecimal("2.10"), "线性代数", "fixture");
        workspace.savePolicy(admin, periodId, 0, MatchingPolicy.defaults(2026), request());
        evidence(admin, period, higher, List.of(), "高等数学");
        evidence(admin, period, lower, List.of(), "大学英语");
        int originalVersion = workspace.view(periodId).settings().revision();
        var initial = matchingService.preview(admin, periodId, originalVersion, null, null, request());
        assertThat(initial.candidates()).isEmpty();
        assertThat(initial.participants()).extracting(MatchingService.RoundParticipant::id)
                .containsExactlyInAnyOrder(higher.getId(), lower.getId());
        assertThat(initial.participants()).allMatch(student -> !student.studentNo().isBlank());
        assertThat(applicationService.listForAdmin(periodId, null)).allMatch(item -> Boolean.TRUE.equals(item.roleChangeAllowed()));

        var change = new ApplicationRoleCorrectionRequest(0, "NAVIGATOR", List.of("线性代数"), "核对领航资格和科目");
        var updated = applicationService.correctRole(admin, higher.getId(), change, request());
        assertThat(updated.participantRole()).isEqualTo("NAVIGATOR");
        assertThat(updated.subjects()).containsExactly("线性代数");
        assertThat(updated.cumulativeGpa()).isEqualByComparingTo("4.30");
        assertThat(updated.selectionStatus()).isEqualTo("MATCHING_POOL");
        assertThat(updated.applicationStatus()).isEqualTo("APPROVED");
        assertBusinessError(() -> applicationService.correctRole(admin, higher.getId(), change, request()), "APPLICATION_VERSION_CONFLICT");
        var refreshed = workspace.view(periodId);
        assertThat(refreshed.students()).anyMatch(item -> item.id() == higher.getId() && item.evidence() == null)
                .anyMatch(item -> item.id() == lower.getId() && item.evidence() != null);
        assertBusinessError(() -> matchingService.publish(admin, initial.round().id(), 0, request()), "MATCHING_VERSION_CONFLICT");
        var regenerated = matchingService.preview(admin, periodId, refreshed.settings().revision(), initial.round().id(), 0, request());
        assertThat(regenerated.candidates()).singleElement().satisfies(pair -> {
            assertThat(pair.navigatorApplicationId()).isEqualTo(higher.getId());
            assertThat(pair.climberApplicationId()).isEqualTo(lower.getId());
            assertThat(pair.matchedSubjects()).containsExactly("线性代数");
        });
        matchingService.publish(admin, regenerated.round().id(), 0, request());
        assertThat(applicationService.listForAdmin(periodId, null)).noneMatch(item -> Boolean.TRUE.equals(item.roleChangeAllowed()));
        assertBusinessError(() -> applicationService.correctRole(admin, higher.getId(),
                new ApplicationRoleCorrectionRequest(1, "CLIMBER", List.of("线性代数"), "旧页面提交"), request()),
                "APPLICATION_ROLE_CORRECTION_CLOSED");
        assertThat(jdbc.queryForObject("SELECT reason FROM operation_log WHERE action='APPLICATION_ROLE_CORRECTION' AND target_id=?",
                String.class, higher.getId().toString())).isEqualTo("核对领航资格和科目");
    }

    @Test
    void surplusSeniorMentorsHelpFreshmenBeforePeerStudyWithoutCourseEvidence() {
        AuthenticatedUser admin = actor(createUser("peer-study-admin", "ADMIN", "结对管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "MATCHING", "大一结对学习");
        long periodId = period.getId();
        var senior = createParticipant(periodId, "peer-study-senior", "大三领航者", "NAVIGATOR", 2024,
                new BigDecimal("4.00"), "线性代数", "fixture");
        var regularClimber = createParticipant(periodId, "peer-study-regular", "大三攀登者", "CLIMBER", 2024,
                new BigDecimal("2.00"), "线性代数", "fixture");
        var surplusSenior = createParticipant(periodId, "peer-study-surplus", "剩余大三领航者", "NAVIGATOR", 2024,
                new BigDecimal("4.00"), "英语听说", "fixture");
        var freshman = workbenchStudent(periodId, "peer-study-freshman", "需要英语帮助", "英语听说");
        var first = workbenchStudent(periodId, "peer-study-first", "甲同学", "高等数学");
        var second = workbenchStudent(periodId, "peer-study-second", "乙同学", "高数");
        var third = workbenchStudent(periodId, "peer-study-third", "丙同学", "线性代数");
        var fourth = workbenchStudent(periodId, "peer-study-fourth", "丁同学", "程序设计基础");
        workspace.savePolicy(admin, periodId, 0, MatchingPolicy.defaults(2026), request());
        workspace.setExclusion(admin, periodId, first.getId(), third.getId(), true, "已核实私下约组", request());

        var draft = matchingService.preview(admin, periodId, 2, null, null, request());
        assertThat(draft.candidates().stream().filter(pair -> "SELECTED".equals(pair.status())))
                .extracting(pair -> List.of(pair.navigatorApplicationId(), pair.climberApplicationId()))
                .containsExactlyInAnyOrder(List.of(senior.getId(), regularClimber.getId()),
                        List.of(surplusSenior.getId(), freshman.getId()), List.of(first.getId(), second.getId()),
                        List.of(third.getId(), fourth.getId()));
        assertThat(draft.candidates()).noneMatch(pair -> pair.navigatorApplicationId().equals(first.getId())
                && pair.climberApplicationId().equals(third.getId()));
        assertThat(draft.candidates().stream().filter(pair -> "PEER".equals(pair.matchKind()) && "SELECTED".equals(pair.status())))
                .allSatisfy(pair -> assertThat(pair.matchedSubjects()).isEmpty())
                .anySatisfy(pair -> assertThat(pair.coverage()).containsEntry("sharedStudySubjects", List.of("高等数学")))
                .anySatisfy(pair -> assertThat(pair.coverage()).containsEntry("sharedStudySubjects", List.of()));
    }

    @Test
    void workbenchOptimizesMutualHelpAndAuditsConflictingManualChanges() {
        AuthenticatedUser admin = actor(createUser("workspace-admin", "ADMIN", "匹配管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "MATCHING", "互助工作台");
        long periodId = period.getId();
        var rules = MatchingPolicy.defaults(2026).rules();
        var strictPeer = new MatchingPolicy.Rule(MatchingPolicy.Kind.PEER, true, 1, null, false, false);
        workspace.savePolicy(admin, periodId, 0, new MatchingPolicy(2026, false, List.of(strictPeer, rules.get(0), rules.get(1))), request());
        var a = workbenchStudent(periodId, "peer-a", "甲同学", "英语听说");
        var b = workbenchStudent(periodId, "peer-b", "乙同学", "C语言");
        var c = workbenchStudent(periodId, "peer-c", "丙同学", "C语言");
        var d = workbenchStudent(periodId, "peer-d", "丁同学", "英语听说");
        var e = workbenchStudent(periodId, "peer-e", "戊同学", "Python");
        var helper = createParticipant(periodId, "peer-helper", "领航测试", "NAVIGATOR", 2025,
                new BigDecimal("3.50"), "Python", "helper");
        evidence(admin, period, a, List.of("线性代数"), "C语言程序设计");
        evidence(admin, period, b, List.of("Python"), "英语听说");
        evidence(admin, period, c, List.of(), "线性代数");
        evidence(admin, period, d, List.of(), "Python");

        int revision = workspace.view(periodId).settings().revision();
        var draft = matchingService.preview(admin, periodId, revision, null, null, request());
        long roundId = draft.round().id();
        assertThat(draft.candidates().stream().filter(pair -> "SELECTED".equals(pair.status())))
                .extracting(pair -> List.of(pair.navigatorApplicationId(), pair.climberApplicationId()))
                .containsExactlyInAnyOrder(List.of(a.getId(), c.getId()), List.of(b.getId(), d.getId()), List.of(helper.getId(), e.getId()));
        var ac = draft.candidates().stream().filter(pair -> pair.navigatorApplicationId().equals(a.getId())
                && pair.climberApplicationId().equals(c.getId())).findFirst().orElseThrow();
        var ab = draft.candidates().stream().filter(pair -> pair.navigatorApplicationId().equals(a.getId())
                && pair.climberApplicationId().equals(b.getId())).findFirst().orElseThrow();
        assertThat(ac.coverage()).containsKeys("firstToSecond", "secondToFirst");
        assertThat(ac.totalScore()).isNull();
        var adjusted = matchingService.adjust(admin, roundId, 0, ac.id(), ab.id(), "核实时间后换组", request());
        assertThat(adjusted.candidates().stream().filter(pair -> "SELECTED".equals(pair.status()))).hasSize(2);
        assertThat(adjusted.candidates().stream().filter(pair -> Boolean.TRUE.equals(pair.manuallySelected())))
                .extracting(MatchCandidateResponse::id).containsExactly(ab.id());
        assertThat(adjusted.adjustments()).singleElement().satisfies(entry -> {
            assertThat(entry.reason()).isEqualTo("核实时间后换组");
            assertThat(entry.before()).contains(ac.id());
            assertThat(entry.after()).contains(ab.id()).doesNotContain(ac.id());
        });
        assertBusinessError(() -> matchingService.adjust(admin, roundId, 0, ab.id(), null, "旧弹窗", request()),
                "MATCHING_VERSION_CONFLICT");
        workspace.setExclusion(admin, periodId, b.getId(), a.getId(), true, "已核实私下约组", request());
        assertBusinessError(() -> matchingService.publish(admin, roundId, 1, request()), "MATCHING_VERSION_CONFLICT");

        var updated = matchingService.preview(admin, periodId, revision + 1, roundId, 1, request());
        assertThat(matchingService.roundView(periodId, roundId).round().status()).isEqualTo("SUPERSEDED");
        assertThat(updated.candidates()).noneMatch(pair -> pair.navigatorApplicationId().equals(a.getId()) && pair.climberApplicationId().equals(b.getId()));
        var pair = updated.candidates().stream().filter(item -> "SELECTED".equals(item.status())
                && "PEER".equals(item.matchKind())).findFirst().orElseThrow();
        matchingService.publish(admin, updated.round().id(), 0, request());
        period.setStatus("CONFIRMATION");
        periodMapper.updateById(period);
        assertThat(matchingService.mine(pair.navigatorUserId())).singleElement().satisfies(item -> {
            assertThat(item.coverage()).isNull();
            assertThat(item.matchKind()).isEqualTo("PEER");
        });
        matchingService.decide(actor(userMapper.selectById(pair.navigatorUserId())), pair.id(),
                new MatchDecisionRequest("CONFIRMED", null), request());
        matchingService.decide(actor(userMapper.selectById(pair.climberUserId())), pair.id(),
                new MatchDecisionRequest("CONFIRMED", null), request());
        assertThat(jdbc.queryForObject("SELECT match_kind FROM support_pair WHERE source_candidate_id=?", String.class, pair.id()))
                .isEqualTo("PEER");
    }

    @Test
    void sendsOnlyApprovedPreviewAndCachesEvidenceWithoutExposingKey() {
        AuthenticatedUser admin = actor(createUser("workspace-provider-admin", "ADMIN", "提取测试管理员"));
        ActivityPeriod period = createPeriod(admin.id(), "REVIEW", "脱敏与缓存");
        var student = workbenchStudent(period.getId(), "provider-a", "测试学生", "线性代数");
        student.setStatementText("我需要线性代数辅导。可以提供编程入门帮助。已经和某同学约好分到一组。我的微信是 demo123456。");
        applicationMapper.updateById(student);
        String fake = "sk-synthetic-test-key-8421";
        assertThat(deepSeek.save(admin, fake, request()).suffix()).isEqualTo("8421");
        assertThat(jdbc.queryForObject("SELECT ciphertext FROM matching_provider WHERE id=1", String.class)).doesNotContain(fake);
        deepSeek.save(admin, fake, request());
        var view = workspace.view(period.getId());
        var record = view.students().getFirst();
        assertThat(record.pairingClue()).isTrue();
        assertThat(record.externalPreview()).isEqualTo("我需要线性代数辅导。可以提供编程入门帮助");
        assertBusinessError(() -> workspace.analyze(admin, period.getId(), student.getId(), record.revision(),
                record.externalPreview(), request()), "MATCHING_EXTERNAL_ANALYSIS_DISABLED");
        var defaults = view.settings().policy();
        workspace.savePolicy(admin, period.getId(), 0, new MatchingPolicy(2026, true, defaults.rules()), request());
        assertBusinessError(() -> workspace.analyze(admin, period.getId(), student.getId(), record.revision(),
                student.getStatementText(), request()), "MATCHING_PREVIEW_CHANGED");
        var facts = new MatchingEvidence(List.of(new MatchingEvidence.Topic("线性代数", null, "我需要线性代数辅导")),
                List.of(new MatchingEvidence.Topic("编程入门", null, "可以提供编程入门帮助")));
        doReturn(facts).when(deepSeek).extract(record.externalPreview());
        assertThat(workspace.analyze(admin, period.getId(), student.getId(), record.revision(), record.externalPreview(), request()).facts())
                .isEqualTo(facts);
        workspace.analyze(admin, period.getId(), student.getId(), record.revision(), record.externalPreview(), request());
        verify(deepSeek).extract(record.externalPreview());
    }

    private ParticipationApplication workbenchStudent(long periodId, String login, String name, String subject) {
        return createParticipant(periodId, login, name, "CLIMBER", 2026, BigDecimal.ZERO, subject, "fixture");
    }

    private void evidence(AuthenticatedUser admin, ActivityPeriod period, ParticipationApplication app, List<String> needs, String offer) {
        app.setStatementText("可以辅导" + offer + "。" + needs.stream().map(course -> "需要辅导" + course).collect(java.util.stream.Collectors.joining("。")));
        applicationMapper.updateById(app);
        var facts = new MatchingEvidence(needs.stream().map(course -> new MatchingEvidence.Topic(course, null, "需要辅导" + course)).toList(),
                List.of(new MatchingEvidence.Topic(offer, null, "可以辅导" + offer)));
        workspace.reviewEvidence(admin, period.getId(), app.getId(), 0, workspace.view(period.getId()).settings().revision(), facts, request());
    }

    private ParticipationApplication createParticipant(Long periodId, String login, String name, String role, int grade,
                                                       BigDecimal gpa, String subject, String wechat) {
        return createParticipant(periodId, login, name, role, grade, gpa, subject, wechat,
                "APPROVED", "MATCHING_POOL");
    }

    private ParticipationApplication createParticipant(Long periodId, String login, String name, String role, int grade,
                                                       BigDecimal gpa, String subject, String wechat,
                                                       String applicationStatus, String selectionStatus) {
        UserAccount user = createApprovedStudent(login, name, grade);
        StudentProfile profile = profileMapper.selectById(user.getId());

        ParticipationApplication application = new ParticipationApplication();
        application.setPeriodId(periodId);
        application.setStudentUserId(user.getId());
        application.setParticipantRole(role);
        application.setCumulativeGpa(gpa);
        application.setSubjectsJson("[\"" + subject + "\"]");
        application.setStatementText("用于第二周完整匹配闭环自动化验证的报名自述内容，保证字段长度有效。");
        application.setLearningStylesJson("[\"计划型\",\"例题驱动\"]");
        application.setPersonalitiesJson("[\"耐心\",\"细致\"]");
        application.setApplicationStatus(applicationStatus);
        application.setSelectionStatus(selectionStatus);
        application.setSubmittedAt(LocalDateTime.now());
        applicationMapper.insert(application);

        ApplicationProfileSnapshot snapshot = new ApplicationProfileSnapshot();
        snapshot.setApplicationId(application.getId());
        snapshot.setStudentUserId(user.getId());
        snapshot.setProfileVersion(1);
        snapshot.setStudentNo(login);
        snapshot.setRealName(name);
        snapshot.setCollege(profile.getCollege());
        snapshot.setMajor(profile.getMajor());
        snapshot.setGrade(grade);
        snapshot.setWechat(wechat);
        snapshot.setPhone("1380000" + String.format("%04d", user.getId()));
        snapshotMapper.insert(snapshot);
        return application;
    }

    private UserAccount createApprovedStudent(String login, String name, int grade) {
        UserAccount user = createUser(login, "STUDENT", name);
        StudentProfile profile = new StudentProfile();
        profile.setUserId(user.getId());
        profile.setStudentNo(login);
        profile.setRealName(name);
        profile.setCollege("信息管理与人工智能学院");
        profile.setMajor("计算机科学与技术");
        profile.setGrade(grade);
        profile.setWechat(login + "-wechat");
        profile.setReviewStatus("APPROVED");
        profile.setSubmittedAt(LocalDateTime.now());
        profile.setProfileVersion(1);
        profileMapper.insert(profile);
        return user;
    }

    private ApplicationSubmitRequest submission(Long periodId, String role, String gpa) {
        return new ApplicationSubmitRequest(periodId, role, new BigDecimal(gpa), List.of("高等数学"),
                "希望通过本期结对明确学习目标，并按照约定持续参与线下帮扶活动。",
                List.of("计划型"), List.of("耐心"));
    }

    private ActivityPeriod createPeriod(Long creatorId, String status, String name) {
        ActivityPeriod period = new ActivityPeriod();
        period.setName(name);
        period.setShortName("W2-" + status);
        period.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        period.setRegistrationStart(now.minusDays(1));
        period.setRegistrationEnd("REGISTRATION".equals(status) ? now.plusDays(1) : now.minusHours(1));
        period.setConfirmationDeadline(now.plusDays(5));
        period.setTrackingStart(now.plusDays(6));
        period.setTrackingEnd(now.plusDays(30));
        period.setAcceptanceStart(now.plusDays(31));
        period.setAcceptanceEnd(now.plusDays(40));
        period.setPhotoMin(4);
        period.setPhotoMax(8);
        period.setSubjectWeight(40);
        period.setMajorWeight(25);
        period.setGradeWeight(15);
        period.setLearningStyleWeight(10);
        period.setPersonalityWeight(5);
        period.setGpaGapWeight(5);
        period.setCreatedBy(creatorId);
        periodMapper.insert(period);
        return period;
    }

    private UserAccount createUser(String login, String type, String name) {
        UserAccount user = new UserAccount();
        user.setLoginName(login);
        user.setPasswordHash(passwordEncoder.encode("WeekTwo2026"));
        user.setAccountType(type);
        user.setDisplayName(name);
        user.setAccountStatus("ACTIVE");
        user.setTokenVersion(0);
        userMapper.insert(user);
        return user;
    }

    private void assertBusinessError(Runnable action, String code) {
        BusinessException exception = catchThrowableOfType(action::run, BusinessException.class);
        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(code);
    }

    private AuthenticatedUser actor(UserAccount user) {
        return new AuthenticatedUser(user.getId(), user.getLoginName(), user.getDisplayName(), user.getAccountType(), user.getTokenVersion());
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest();
    }
}
