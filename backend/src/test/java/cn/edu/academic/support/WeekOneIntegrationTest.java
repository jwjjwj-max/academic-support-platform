package cn.edu.academic.support;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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
