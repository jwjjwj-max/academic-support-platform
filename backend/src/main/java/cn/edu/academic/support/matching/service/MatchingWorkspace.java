package cn.edu.academic.support.matching.service;

import cn.edu.academic.support.application.mapper.ApplicationProfileSnapshotMapper;
import cn.edu.academic.support.application.mapper.ParticipationApplicationMapper;
import cn.edu.academic.support.application.model.ApplicationProfileSnapshot;
import cn.edu.academic.support.application.model.ParticipationApplication;
import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.matching.dto.MatchingEvidence;
import cn.edu.academic.support.matching.dto.MatchingEvidence.Topic;
import cn.edu.academic.support.matching.dto.MatchingPolicy;
import cn.edu.academic.support.matching.service.MatchingPlanner.Key;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.security.AuthenticatedUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchingWorkspace {
    private static final List<String> INPUT_STAGES = List.of("REVIEW", "MATCHING", "CONFIRMATION");
    private static final Pattern PRIVATE_LINE = Pattern.compile(
            "约好|指定.{0,8}(同学|搭档)|搭档|组队|结对|分到一起|分在一起|同一组|一组|互相帮扶对象|私下|微信|学号|手机|电话|邮箱|联系我|QQ",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IDENTIFIER = Pattern.compile("https?://\\S+|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}|[A-Za-z0-9_-]*[0-9]{5,}[A-Za-z0-9_-]*",
            Pattern.CASE_INSENSITIVE);

    private final ActivityPeriodMapper periods;
    private final ParticipationApplicationMapper applications;
    private final ApplicationProfileSnapshotMapper snapshots;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Validator validator;
    private final TransactionTemplate transactions;
    private final DeepSeekMatchingClient deepSeek;
    private final AuditService audit;
    private final Set<Long> analyzing = ConcurrentHashMap.newKeySet();

    public record PolicyVersion(int revision, MatchingPolicy policy) {}
    public record EvidenceRecord(int applicationRevision, MatchingEvidence facts, boolean reviewed) {}
    public record Exclusion(long firstApplicationId, long secondApplicationId, String reason) {}
    public record Student(long id, int revision, String name, String studentNo, int grade, String role,
                          List<String> subjects, String statement, String externalPreview, boolean pairingClue,
                          EvidenceRecord evidence) {}
    public record WorkspaceView(PolicyVersion settings, List<Student> students, List<Exclusion> exclusions) {}

    @Transactional(readOnly = true)
    public WorkspaceView view(Long periodId) {
        ActivityPeriod period = period(periodId, false);
        List<ApplicationProfileSnapshot> periodSnapshots = periodSnapshots(periodId);
        Map<Long, ApplicationProfileSnapshot> profileByApp = new HashMap<>();
        periodSnapshots.forEach(snapshot -> profileByApp.put(snapshot.getApplicationId(), snapshot));
        var names = names(periodSnapshots);
        var evidence = evidence(periodId);
        var students = pool(periodId).stream().map(app -> {
            var profile = profileByApp.get(app.getId());
            if (profile == null) throw new BusinessException("APPLICATION_SNAPSHOT_MISSING", "报名资料快照不存在");
            String statement = app.getStatementText();
            return new Student(app.getId(), app.getRevision(), profile.getRealName(), profile.getStudentNo(), profile.getGrade(),
                    app.getParticipantRole(), subjects(app), statement, sanitize(statement, names),
                    Arrays.stream(statement.split("[。！？；;\\r\\n]+"))
                            .anyMatch(line -> line.matches(".*(约好|指定.{0,8}(同学|搭档)|搭档|组队|结对|分到一起|分在一起|同一组|一组|帮扶对象|私下).*")),
                    currentEvidence(evidence.get(app.getId()), app));
        }).toList();
        return new WorkspaceView(policy(period), students, exclusions(periodId));
    }

    @Transactional
    public PolicyVersion savePolicy(AuthenticatedUser actor, Long periodId, int revision, MatchingPolicy policy,
                                    HttpServletRequest request) {
        ActivityPeriod period = period(periodId, true);
        if (!List.of("REVIEW", "MATCHING").contains(period.getStatus()) || hasPublished(periodId)) {
            throw new BusinessException("MATCHING_POLICY_CLOSED", "规则可在报名审核或首次发布前的匹配阶段配置");
        }
        PolicyVersion before = policy(period);
        checkRevision(revision, before.revision());
        if (policy.rules().stream().map(MatchingPolicy.Rule::kind).distinct().count() != MatchingPolicy.Kind.values().length
                || policy.rules().stream().noneMatch(MatchingPolicy.Rule::enabled)
                || policy.rules().stream().anyMatch(rule -> rule.kind() != MatchingPolicy.Kind.REGULAR &&
                (rule.allowOlderNavigator() || rule.requireHigherGpa()))) {
            throw new BusinessException("INVALID_MATCHING_RULES", "三种规则须各出现一次且至少启用一项，年级扩展和绩点门槛仅用于常规帮扶");
        }
        PolicyVersion after = new PolicyVersion(before.revision() + 1, policy);
        writePolicy(periodId, before, after);
        audit.log(actor, "MATCHING_POLICY_SAVE", "PERIOD", periodId, before, after, null, request);
        return after;
    }

    public EvidenceRecord analyze(AuthenticatedUser actor, Long periodId, Long applicationId, int expectedRevision,
                                  String confirmedPreview, HttpServletRequest request) {
        if (!analyzing.add(applicationId)) throw new BusinessException("MATCHING_ANALYSIS_IN_PROGRESS", "该报名正在分析", HttpStatus.CONFLICT);
        try {
            ActivityPeriod period = inputPeriod(periodId, false);
            requireExternalPermission(period);
            ParticipationApplication app = requirePoolMember(periodId, applicationId);
            checkRevision(expectedRevision, app.getRevision());
            EvidenceRecord cached = currentEvidence(evidence(periodId).get(applicationId), app);
            if (cached != null) return cached;
            String preview = sanitize(app.getStatementText(), names(periodSnapshots(periodId)));
            if (preview.isBlank()) throw new BusinessException("MATCHING_EVIDENCE_EMPTY", "没有可提交的脱敏学业内容，可人工录入");
            if (!preview.equals(confirmedPreview)) throw new BusinessException("MATCHING_PREVIEW_CHANGED", "脱敏预览已更新，请重新核对", HttpStatus.CONFLICT);
            MatchingEvidence facts = validateEvidence(deepSeek.extract(preview), preview);
            return transactions.execute(status -> {
                ActivityPeriod locked = inputPeriod(periodId, true);
                requireExternalPermission(locked);
                ParticipationApplication latest = requirePoolMember(periodId, applicationId);
                checkRevision(expectedRevision, latest.getRevision());
                EvidenceRecord updated = currentEvidence(evidence(periodId).get(applicationId), latest);
                if (updated != null) return updated;
                return storeEvidence(actor, locked, latest, facts, false, request);
            });
        } finally {
            analyzing.remove(applicationId);
        }
    }

    @Transactional
    public EvidenceRecord reviewEvidence(AuthenticatedUser actor, Long periodId, Long applicationId, int expectedRevision,
                                         int workspaceRevision, MatchingEvidence facts, HttpServletRequest request) {
        ActivityPeriod period = inputPeriod(periodId, true);
        checkRevision(workspaceRevision, policy(period).revision());
        ParticipationApplication app = requirePoolMember(periodId, applicationId);
        checkRevision(expectedRevision, app.getRevision());
        return storeEvidence(actor, period, app, validateEvidence(facts, app.getStatementText()), true, request);
    }

    @Transactional
    public void setExclusion(AuthenticatedUser actor, Long periodId, long a, long b, boolean excluded,
                             String reason, HttpServletRequest request) {
        ActivityPeriod period = inputPeriod(periodId, true);
        if (a == b) throw new BusinessException("INVALID_MATCHING_EXCLUSION", "请选择两位不同的学生");
        requirePoolMember(periodId, a);
        requirePoolMember(periodId, b);
        Key key = Key.of(a, b);
        Exclusion before = exclusions(periodId).stream()
                .filter(item -> item.firstApplicationId() == key.first() && item.secondApplicationId() == key.second())
                .findFirst().orElse(null);
        if (!excluded && before == null) return;
        if (excluded && before == null && publishedTogether(periodId, key)) {
            throw new BusinessException("MATCHING_EXCLUSION_PUBLISHED", "这组已经发布，请先处理现有帮扶关系再登记排除");
        }
        Exclusion after = null;
        if (excluded) {
            after = new Exclusion(key.first(), key.second(), reason.strip());
            if (after.equals(before)) return;
            if (before == null) {
                jdbc.update("INSERT INTO matching_exclusion(period_id, first_application_id, second_application_id, reason) VALUES (?,?,?,?)",
                        periodId, key.first(), key.second(), after.reason());
            } else {
                jdbc.update("UPDATE matching_exclusion SET reason=? WHERE period_id=? AND first_application_id=? AND second_application_id=?",
                        after.reason(), periodId, key.first(), key.second());
            }
        } else {
            jdbc.update("DELETE FROM matching_exclusion WHERE period_id=? AND first_application_id=? AND second_application_id=?",
                    periodId, key.first(), key.second());
        }
        bump(period);
        audit.log(actor, "MATCHING_EXCLUSION_CHANGE", "PERIOD", periodId, before, after, reason.strip(), request);
    }

    PolicyVersion policy(ActivityPeriod period) {
        return jdbc.query("SELECT revision, policy_json FROM matching_policy WHERE period_id=?", (rs, index) ->
                        new PolicyVersion(rs.getInt(1), decode(rs.getString(2), MatchingPolicy.class)), period.getId())
                .stream().findFirst().orElseGet(() -> new PolicyVersion(0, MatchingPolicy.defaults(period.getRegistrationStart().getYear())));
    }

    Set<Key> blocked(Long periodId) {
        return exclusions(periodId).stream().map(item -> Key.of(item.firstApplicationId(), item.secondApplicationId()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    List<MatchingPlanner.Participant> participants(Long periodId, Set<Long> unavailable) {
        Map<Long, Integer> grades = new HashMap<>();
        periodSnapshots(periodId).forEach(snapshot -> grades.put(snapshot.getApplicationId(), snapshot.getGrade()));
        var evidence = evidence(periodId);
        return pool(periodId).stream().filter(app -> !unavailable.contains(app.getId())).map(app -> {
            Integer grade = grades.get(app.getId());
            if (grade == null) throw new BusinessException("APPLICATION_SNAPSHOT_MISSING", "报名资料快照不存在");
            EvidenceRecord facts = currentEvidence(evidence.get(app.getId()), app);
            return new MatchingPlanner.Participant(app, grade, subjects(app), facts == null ? MatchingEvidence.empty() : facts.facts());
        }).toList();
    }

    static void checkRevision(int expected, int current) {
        if (expected != current) throw new BusinessException("MATCHING_VERSION_CONFLICT", "规则、依据或方案已更新，请刷新后再操作", HttpStatus.CONFLICT);
    }

    private boolean hasPublished(Long periodId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM match_round WHERE period_id=? AND status='PUBLISHED'",
                Integer.class, periodId) > 0;
    }

    public boolean allowsApplicationRoleCorrection(ActivityPeriod period) {
        return List.of("REVIEW", "MATCHING").contains(period.getStatus()) && !hasPublished(period.getId());
    }

    // The caller holds the period lock shared with matching preview and publication.
    public void invalidateForRoleCorrection(ActivityPeriod period) {
        var current = policy(period);
        if (current.revision() > 0) {
            writePolicy(period.getId(), current, new PolicyVersion(current.revision() + 1, current.policy()));
        }
        jdbc.update("""
                UPDATE match_round SET status='SUPERSEDED'
                WHERE period_id=? AND status='DRAFT' AND policy_revision IS NULL
                """, period.getId());
    }

    private boolean publishedTogether(Long periodId, Key key) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_candidate c JOIN match_round r ON c.round_id=r.id
                WHERE r.period_id=? AND c.status IN ('PUBLISHED','CONFIRMED')
                AND ((c.navigator_application_id=? AND c.climber_application_id=?)
                    OR (c.navigator_application_id=? AND c.climber_application_id=?))
                """, Integer.class, periodId, key.first(), key.second(), key.second(), key.first()) > 0;
    }

    private List<Exclusion> exclusions(Long periodId) {
        return jdbc.query("SELECT first_application_id, second_application_id, reason FROM matching_exclusion WHERE period_id=? ORDER BY first_application_id, second_application_id",
                (rs, row) -> new Exclusion(rs.getLong(1), rs.getLong(2), rs.getString(3)), periodId);
    }

    private Map<Long, EvidenceRecord> evidence(Long periodId) {
        return jdbc.query("""
                SELECT e.application_id, e.application_revision, e.facts_json, e.reviewed
                FROM matching_evidence e JOIN application a ON a.id=e.application_id WHERE a.period_id=?
                """, (org.springframework.jdbc.core.ResultSetExtractor<Map<Long, EvidenceRecord>>) rs -> {
            Map<Long, EvidenceRecord> values = new HashMap<>();
            while (rs.next()) values.put(rs.getLong(1), new EvidenceRecord(rs.getInt(2),
                    decode(rs.getString(3), MatchingEvidence.class), rs.getBoolean(4)));
            return values;
        }, periodId);
    }

    private EvidenceRecord currentEvidence(EvidenceRecord evidence, ParticipationApplication app) {
        return evidence != null && evidence.applicationRevision() == app.getRevision() ? evidence : null;
    }

    private EvidenceRecord storeEvidence(AuthenticatedUser actor, ActivityPeriod period, ParticipationApplication app,
                                         MatchingEvidence facts, boolean reviewed, HttpServletRequest request) {
        String body = encode(facts);
        String update = "UPDATE matching_evidence SET application_revision=?, facts_json=?, reviewed=? WHERE application_id=?";
        if (jdbc.update(update, app.getRevision(), body, reviewed, app.getId()) == 0) {
            jdbc.update("INSERT INTO matching_evidence(application_id, application_revision, facts_json, reviewed) VALUES (?,?,?,?)",
                    app.getId(), app.getRevision(), body, reviewed);
        }
        bump(period);
        audit.log(actor, reviewed ? "MATCHING_EVIDENCE_REVIEW" : "MATCHING_DEEPSEEK_ANALYZE", "APPLICATION", app.getId(),
                null, Map.of("needs", facts.needs().size(), "offers", facts.offers().size()), null, request);
        return new EvidenceRecord(app.getRevision(), facts, reviewed);
    }

    private MatchingEvidence validateEvidence(MatchingEvidence facts, String source) {
        if (facts == null || !validator.validate(facts).isEmpty()) {
            throw new BusinessException("INVALID_MATCHING_EVIDENCE", "提取结果格式不正确，请重试或人工复核");
        }
        return new MatchingEvidence(validateTopics(facts.needs(), source), validateTopics(facts.offers(), source));
    }

    private List<Topic> validateTopics(List<Topic> facts, String source) {
        return facts.stream().map(topic -> {
            String quote = topic.quote().strip();
            if (!source.contains(quote) || PRIVATE_LINE.matcher(quote).find() || IDENTIFIER.matcher(quote).find()) {
                throw new BusinessException("INVALID_MATCHING_QUOTE", "每项依据须引用学业自述原文，请去掉指名、联系信息或约组内容");
            }
            if (!MatchingPlanner.mentionsCourse(quote, topic.course()) || !MatchingPlanner.mentionsPoint(quote, topic.knowledgePoint())) {
                throw new BusinessException("MATCHING_TOPIC_NOT_GROUNDED", "课程和知识点须能在所引原文中找到，请复核提取结果");
            }
            return new Topic(topic.course().strip(), topic.knowledgePoint() == null ? null : topic.knowledgePoint().strip(), quote);
        }).distinct().toList();
    }

    private void requireExternalPermission(ActivityPeriod period) {
        if (!policy(period).policy().allowExternalAnalysis()) {
            throw new BusinessException("MATCHING_EXTERNAL_ANALYSIS_DISABLED", "请先确认外发许可并启用本期 DeepSeek 分析");
        }
    }

    private void bump(ActivityPeriod period) {
        PolicyVersion before = policy(period);
        writePolicy(period.getId(), before, new PolicyVersion(before.revision() + 1, before.policy()));
    }

    private void writePolicy(Long periodId, PolicyVersion before, PolicyVersion after) {
        if (before.revision() == 0) {
            jdbc.update("INSERT INTO matching_policy(period_id, revision, policy_json) VALUES (?,?,?)",
                    periodId, after.revision(), encode(after.policy()));
        } else {
            jdbc.update("UPDATE matching_policy SET revision=?, policy_json=? WHERE period_id=?",
                    after.revision(), encode(after.policy()), periodId);
        }
    }

    private ActivityPeriod period(Long id, boolean lock) {
        ActivityPeriod period = lock ? periods.lockById(id) : periods.selectById(id);
        if (period == null) throw new BusinessException("PERIOD_NOT_FOUND", "期次不存在", HttpStatus.NOT_FOUND);
        return period;
    }

    private ActivityPeriod inputPeriod(Long id, boolean lock) {
        ActivityPeriod period = period(id, lock);
        if (!INPUT_STAGES.contains(period.getStatus())) {
            throw new BusinessException("MATCHING_INPUT_CLOSED", "当前阶段不可更改匹配依据或排除组合");
        }
        return period;
    }

    private ParticipationApplication requirePoolMember(Long periodId, long id) {
        ParticipationApplication app = applications.selectById(id);
        if (app == null || !periodId.equals(app.getPeriodId()) || !"APPROVED".equals(app.getApplicationStatus())
                || !"MATCHING_POOL".equals(app.getSelectionStatus())) {
            throw new BusinessException("MATCHING_STUDENT_NOT_IN_POOL", "该学生不在本期匹配池");
        }
        return app;
    }

    private List<ParticipationApplication> pool(Long periodId) {
        return applications.selectList(Wrappers.<ParticipationApplication>lambdaQuery()
                .eq(ParticipationApplication::getPeriodId, periodId).eq(ParticipationApplication::getApplicationStatus, "APPROVED")
                .eq(ParticipationApplication::getSelectionStatus, "MATCHING_POOL").orderByAsc(ParticipationApplication::getId));
    }

    private List<ApplicationProfileSnapshot> periodSnapshots(Long periodId) {
        var ids = applications.selectList(Wrappers.<ParticipationApplication>lambdaQuery().select(ParticipationApplication::getId)
                .eq(ParticipationApplication::getPeriodId, periodId)).stream().map(ParticipationApplication::getId).toList();
        return ids.isEmpty() ? List.of() : snapshots.selectList(Wrappers.<ApplicationProfileSnapshot>lambdaQuery()
                .in(ApplicationProfileSnapshot::getApplicationId, ids));
    }

    private List<String> names(List<ApplicationProfileSnapshot> snapshots) {
        return snapshots.stream().map(ApplicationProfileSnapshot::getRealName).filter(name -> name != null && name.length() >= 2)
                .distinct().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    static String sanitize(String statement, List<String> names) {
        return Arrays.stream(statement.split("[。！？；;\\r\\n]+"))
                .filter(line -> !PRIVATE_LINE.matcher(line).find())
                .map(line -> {
                    String scrubbed = IDENTIFIER.matcher(line).replaceAll("[已脱敏]");
                    for (String name : names) scrubbed = scrubbed.replace(name, "[同学]");
                    return scrubbed.strip();
                }).filter(line -> !line.isBlank()).collect(Collectors.joining("。"));
    }

    private List<String> subjects(ParticipationApplication app) {
        try { return json.readValue(app.getSubjectsJson(), new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }

    private <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }
}
