package cn.edu.academic.support;

import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.user.mapper.UserAccountMapper;
import cn.edu.academic.support.user.model.UserAccount;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WeekOneIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountMapper userAccountMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AuditService auditService;

    @BeforeEach
    void seedPrivilegedAccounts() {
        ensureUser("owner01", "OwnerPass2026", "SYSTEM_OWNER", "系统负责人");
        ensureUser("admin01", "AdminPass2026", "ADMIN", "学习部管理员");
    }

    @Test
    void registrationRequiresManualReviewAndRejectedStudentCanResubmit() throws Exception {
        register("23020116", "陈晨");
        String studentToken = login("23020116", "Student2026");
        mvc.perform(get("/students/me/profile").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewStatus").value("PENDING"));

        String adminToken = login("admin01", "AdminPass2026");
        Long studentId = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getLoginName, "23020116")).getId();
        mvc.perform(post("/admin/students/{id}/review", studentId)
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"reason\":\"年级信息需核对\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewStatus").value("REJECTED"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/students/me/profile")
                        .header("Authorization", bearer(studentToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"23020116\",\"realName\":\"陈晨\",\"college\":\"信息技术与人工智能学院\",\"major\":\"计算机科学与技术\",\"grade\":2023,\"wechat\":\"chenchen\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.profileVersion").value(2));
    }

    @Test
    void studentCannotUseAdminOrOwnerEndpoints() throws Exception {
        register("23020117", "周雨");
        String token = login("23020117", "Student2026");
        mvc.perform(get("/admin/students").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/owner/admins").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanCreateAndDisableAdminAndOldTokenStopsWorking() throws Exception {
        String ownerToken = login("owner01", "OwnerPass2026");
        String response = mvc.perform(post("/owner/admins").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"admin02\",\"displayName\":\"第二管理员\",\"initialPassword\":\"SecondAdmin2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long adminId = objectMapper.readTree(response).path("data").path("id").asLong();
        String adminToken = login("admin02", "SecondAdmin2026");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/owner/admins/{id}/status", adminId)
                        .header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"工作人员离岗\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/periods").header("Authorization", bearer(adminToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void periodRejectsWeightSumOtherThanOneHundred() throws Exception {
        String adminToken = login("admin01", "AdminPass2026");
        mvc.perform(post("/periods").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(periodJson(39)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/periods").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(periodJson(40)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.subjectWeight").value(40));
    }

    @Test
    void registrationAcceptsOnlyCanonicalAcademicFields() throws Exception {
        for(String major: java.util.List.of("人工智能","软件工程","计算机科学与技术")) {
            String no="major"+Math.abs(major.hashCode());
            var body=objectMapper.createObjectNode().put("studentNo",no).put("realName","专业测试").put("college","信息技术与人工智能学院").put("major",major).put("grade",2026).put("wechat","test").put("phone","").put("password","Student2026").put("confirmPassword","Student2026");
            mvc.perform(post("/students/register").contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk());
        }
        long before=userAccountMapper.selectCount(Wrappers.emptyWrapper());
        for(String major: java.util.List.of("24软工","随意填写")) {
            var body=objectMapper.createObjectNode().put("studentNo","invalidmajor").put("realName","测试").put("college","信息技术与人工智能学院").put("major",major).put("grade",2026).put("wechat","test").put("phone","").put("password","Student2026").put("confirmPassword","Student2026");
            mvc.perform(post("/students/register").contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.major").exists());
        }
        assertThat(userAccountMapper.selectCount(Wrappers.emptyWrapper())).isEqualTo(before);
    }

    @Test
    void changingProfileCannotBypassAcademicFieldValidation() throws Exception {
        register("profilevalidation", "测试"); String token=login("profilevalidation", "Student2026");
        var body=objectMapper.createObjectNode().put("studentNo","profilevalidation").put("realName","测试").put("college","任意学院").put("major","24软工").put("grade",2026).put("wechat","test").put("phone","");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/students/me/profile").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.college").exists()).andExpect(jsonPath("$.data.major").exists());
    }

    @Test
    void reviewApiExposesTotalAndDisjointPagesBeyondTwentyStudents() throws Exception {
        for(int i=0;i<21;i++) register("pagingstudent"+i,"分页测试"+i);
        String token=login("admin01","AdminPass2026");
        var first=objectMapper.readTree(mvc.perform(get("/admin/students").param("page","1").param("size","20").header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        var second=objectMapper.readTree(mvc.perform(get("/admin/students").param("page","2").param("size","20").header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(first.path("total").asLong()).isGreaterThan(20);
        assertThat(first.path("records").size()).isEqualTo(20);
        assertThat(second.path("records").size()).isGreaterThan(0);
        java.util.Set<Long> ids=new java.util.HashSet<>();first.path("records").forEach(row->ids.add(row.path("userId").asLong()));
        second.path("records").forEach(row->assertThat(ids).doesNotContain(row.path("userId").asLong()));
        mvc.perform(get("/admin/students").param("status", "PENDING").param("keyword", " PAGINGSTUDENT20 ")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].studentNo").value("pagingstudent20"));
        mvc.perform(get("/admin/students").param("status", "APPROVED").param("keyword", "分页测试")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/admin/students").param("keyword", "%_").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void correctsApplicationWithAuditAndRollsBackACombinedApprovalOnAuditFailure() throws Exception {
        String admin = bearer(login("admin01", "AdminPass2026"));
        register("correction2026", "报名更正测试");
        String student = bearer(login("correction2026", "Student2026"));
        Long studentId = jdbc.queryForObject("SELECT id FROM sys_user WHERE login_name = 'correction2026'", Long.class);
        mvc.perform(post("/admin/students/{id}/review", studentId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk());
        String createdPeriod = mvc.perform(post("/periods").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(periodJson(40).replace("2026秋-40", "更正测试")
                                .replace("秋期 40", "秋期更正测试")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long period = objectMapper.readTree(createdPeriod).path("data").path("id").asLong();
        jdbc.update("""
                UPDATE activity_period SET status = 'REGISTRATION', registration_start = ?, registration_end = ? WHERE id = ?
                """, java.time.LocalDateTime.now().minusHours(1), java.time.LocalDateTime.now().plusHours(1), period);
        var application = objectMapper.createObjectNode().put("periodId", period).put("participantRole", "CLIMBER")
                .put("cumulativeGpa", 3.4).put("statementText", "这是一段用于测试报名审核的自述，描述需要补习高数与英语的具体学习目标和目前基础。");
        application.putArray("learningStyles").add("例题驱动");
        application.putArray("personalities").add("细致");
        for (String invalid : java.util.List.of("[\"高数，英语\"]", "[\"高数\",\" 高数 \"]", "[\"　\"]",
                "[\"" + "A".repeat(81) + "\"]", "[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"]")) {
            application.set("subjects", objectMapper.readTree(invalid));
            mvc.perform(post("/applications").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                            .content(application.toString()))
                    .andExpect(status().isBadRequest());
        }
        application.putArray("subjects").add("English Listening").add("高等数学");
        String created = mvc.perform(post("/applications").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON).content(application.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").path("id").asLong();
        jdbc.update("UPDATE activity_period SET status = 'REVIEW' WHERE id = ?", period);
        String url = "/admin/applications/" + id + "/correction";
        var correction = objectMapper.createObjectNode().put("revision", 0).put("currentProfileVersion", 1)
                .put("grade", 2025).put("major", "人工智能").put("cumulativeGpa", 0).put("reason", "已与学生核对");
        correction.putArray("subjects").add("线性代数");
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(correction.toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", student).contentType(MediaType.APPLICATION_JSON).content(correction.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(correction.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.applicationStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.selectionStatus").value("UNDECIDED"));
        mvc.perform(post(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(correction.toString()))
                .andExpect(status().isConflict());
        mvc.perform(get("/applications/mine").header("Authorization", student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].grade").value(2025))
                .andExpect(jsonPath("$.data[0].major").value("人工智能"))
                .andExpect(jsonPath("$.data[0].cumulativeGpa").value(0))
                .andExpect(jsonPath("$.data[0].subjects[0]").value("线性代数"));
        mvc.perform(get("/students/me/profile").header("Authorization", student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.grade").value(2025))
                .andExpect(jsonPath("$.data.major").value("人工智能"))
                .andExpect(jsonPath("$.data.profileVersion").value(2))
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"));
        mvc.perform(get("/admin/audit-logs").param("action", "APPLICATION_CORRECTION").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].actorLogin").value("admin01"))
                .andExpect(jsonPath("$.data[0].reason").value("已与学生核对"));
        var log = jdbc.queryForObject("""
                SELECT actor_login, reason, before_snapshot, after_snapshot FROM operation_log
                WHERE action = 'APPLICATION_CORRECTION' AND target_id = ?
                """, (row, index) -> java.util.List.of(row.getString("before_snapshot"), row.getString("after_snapshot")),
                Long.toString(id));
        assertThat(objectMapper.readTree(log.get(0)).path("grade").asInt()).isEqualTo(2023);
        assertThat(objectMapper.readTree(log.get(1)).path("grade").asInt()).isEqualTo(2025);

        correction.remove("subjects");
        correction.put("approve", true).put("revision", 1).put("grade", 2026);
        mvc.perform(post(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(correction.toString()))
                .andExpect(status().isConflict());
        correction.put("currentProfileVersion", 2);
        doThrow(new IllegalStateException("audit failure")).when(auditService).log(
                any(), eq("APPLICATION_CORRECTION"), eq("APPLICATION"), eq(id), any(), any(), any(), any());
        mvc.perform(post(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(correction.toString()))
                .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT grade FROM student_profile WHERE user_id = ?", Integer.class, studentId)).isEqualTo(2025);
        assertThat(jdbc.queryForObject("SELECT grade FROM application_profile_snapshot WHERE application_id = ?", Integer.class, id)).isEqualTo(2025);
        assertThat(jdbc.queryForObject("SELECT revision FROM application WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT application_status FROM application WHERE id = ?", String.class, id)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_review_history WHERE application_id = ? AND action = 'APPROVE'", Long.class, id)).isZero();
        org.mockito.Mockito.reset(auditService);
        mvc.perform(post(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(correction.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.grade").value(2026))
                .andExpect(jsonPath("$.data.applicationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.selectionStatus").value("UNDECIDED"));
        jdbc.update("UPDATE activity_period SET status = 'MATCHING' WHERE id = ?", period);
        correction.put("revision", 2).put("currentProfileVersion", 3).put("approve", false);
        mvc.perform(post(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(correction.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APPLICATION_CORRECTION_NOT_OPEN"));
    }

    private void register(String studentNo, String name) throws Exception {
        mvc.perform(post("/students/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"studentNo":"%s","realName":"%s","college":"信息技术与人工智能学院","major":"计算机科学与技术","grade":2023,"wechat":"wechat_%s","phone":"13800000000","password":"Student2026","confirmPassword":"Student2026"}
                """.formatted(studentNo, name, studentNo))).andExpect(status().isOk());
    }

    private String login(String loginName, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"%s\",\"password\":\"%s\"}".formatted(loginName, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        String token = json.path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private void ensureUser(String login, String password, String type, String displayName) {
        if (userAccountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getLoginName, login)) > 0) return;
        UserAccount user = new UserAccount();
        user.setLoginName(login); user.setPasswordHash(passwordEncoder.encode(password)); user.setAccountType(type);
        user.setDisplayName(displayName); user.setAccountStatus("ACTIVE"); user.setTokenVersion(0);
        userAccountMapper.insert(user);
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String periodJson(int subjectWeight) {
        return """
                {"name":"2026—2027 学年秋期 %d","shortName":"2026秋-%d","status":"ANNOUNCED","registrationStart":"2026-09-01T00:00:00","registrationEnd":"2026-09-15T23:59:00","confirmationDeadline":"2026-09-20T23:59:00","trackingStart":"2026-10-01T00:00:00","trackingEnd":"2026-12-15T23:59:00","acceptanceStart":"2026-12-16T00:00:00","acceptanceEnd":"2027-01-10T23:59:00","photoMin":4,"photoMax":8,"subjectWeight":%d,"majorWeight":25,"gradeWeight":15,"learningStyleWeight":10,"personalityWeight":5,"gpaGapWeight":5}
                """.formatted(subjectWeight, subjectWeight, subjectWeight);
    }
}
