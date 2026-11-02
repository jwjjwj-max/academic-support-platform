package cn.edu.academic.support;

import cn.edu.academic.support.application.mapper.*;
import cn.edu.academic.support.application.model.*;
import cn.edu.academic.support.audit.mapper.OperationLogMapper;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.grade.dto.*;
import cn.edu.academic.support.grade.mapper.*;
import cn.edu.academic.support.grade.service.GradeService;
import cn.edu.academic.support.matching.mapper.SupportPairMapper;
import cn.edu.academic.support.matching.model.SupportPair;
import cn.edu.academic.support.moral.service.MoralFileService;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.security.AuthenticatedUser;
import cn.edu.academic.support.user.mapper.UserAccountMapper;
import cn.edu.academic.support.user.model.UserAccount;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class WeekFourIntegrationTest {
 @Autowired GradeService grades; @Autowired MoralFileService moral; @Autowired UserAccountMapper users;
 @Autowired ActivityPeriodMapper periods; @Autowired ParticipationApplicationMapper applications;
 @Autowired ApplicationProfileSnapshotMapper snapshots; @Autowired SupportPairMapper pairs;
 @Autowired ParticipantTermGradeMapper gradeMapper; @Autowired GradeCorrectionHistoryMapper corrections; @Autowired GradeCohortStatMapper cohortStats;
 @Autowired OperationLogMapper logs; @Autowired PasswordEncoder encoder; @Autowired MockMvc mvc;

 @Test void gradePreviewImportsOnlyFormalParticipantsAndUsesExactCreditGpaHeaders() throws Exception {
  Scenario s=scenario("import"); var preview=grades.preview(actor(s.admin),s.period.getId(),"BEFORE","帮扶前成绩",sheet(s),request());
  assertThat(preview.suggestedMapping()).containsEntry("termGpa","平均学分绩点").containsEntry("termGpaRank","平均学分绩点排名");
  assertThat(preview.suggestedMapping().get("termGpa")).isNotEqualTo("平均绩点");
  assertThat(preview.batch().totalRows()).isEqualTo(3);assertThat(preview.batch().participantRows()).isEqualTo(2);
  var imported=grades.importBatch(actor(s.admin),preview.batch().id(),new GradeImportRequest(preview.suggestedMapping()),request());
  assertThat(imported.status()).isEqualTo("IMPORTED");assertThat(gradeMapper.selectCount(Wrappers.emptyWrapper())).isEqualTo(2);
  assertThat(gradeMapper.selectList(Wrappers.emptyWrapper())).extracting("studentNoSnapshot").containsExactlyInAnyOrder(s.navNo,s.climbNo).doesNotContain("999999");
  assertThat(gradeMapper.selectList(Wrappers.emptyWrapper())).extracting("professionalStudentCount").containsOnly(2);
  assertThat(cohortStats.selectList(Wrappers.emptyWrapper())).hasSize(2);
  assertThat(grades.cohortStats(s.period.getId())).filteredOn(x->x.grade().equals("2024")&&x.major().equals("软件工程"))
          .singleElement().extracting("studentCount").isEqualTo(2);
  assertThat(grades.adminGrades(s.period.getId())).extracting("professionalTopPercent").containsExactlyInAnyOrder(new BigDecimal("50.0"),new BigDecimal("100.0"));
 }

 @Test void invalidMappingIsRejectedAndCorrectionKeepsBeforeAfterReasonAndOperator() throws Exception {
  Scenario s=scenario("correct");var preview=grades.preview(actor(s.admin),s.period.getId(),"BEFORE","帮扶前成绩",sheet(s),request());
  Map<String,String> wrong=new LinkedHashMap<>(preview.suggestedMapping());wrong.put("termGpa","平均绩点");
  assertError(()->grades.importBatch(actor(s.admin),preview.batch().id(),new GradeImportRequest(wrong),request()),"GRADE_MAPPING_WRONG_GPA");
  grades.importBatch(actor(s.admin),preview.batch().id(),new GradeImportRequest(preview.suggestedMapping()),request());
  var grade=gradeMapper.selectList(Wrappers.emptyWrapper()).getFirst();BigDecimal old=grade.getTermGpa();
  assertError(()->grades.correct(actor(s.admin),grade.getId(),new GradeCorrectionRequest(new BigDecimal("4.25"),3,"排名超出专业人数"),request()),"GRADE_RANK_EXCEEDS_COHORT");
  grades.correct(actor(s.admin),grade.getId(),new GradeCorrectionRequest(new BigDecimal("4.25"),2,"教务处复核原表录入错误"),request());
  var h=corrections.selectList(Wrappers.emptyWrapper()).getFirst();assertThat(h.getOldTermGpa()).isEqualByComparingTo(old);assertThat(h.getNewTermGpa()).isEqualByComparingTo("4.25");
  assertThat(h.getReason()).isEqualTo("教务处复核原表录入错误");assertThat(h.getOperatorId()).isEqualTo(s.admin.getId());
  assertThat(logs.selectList(Wrappers.emptyWrapper())).extracting("action").contains("GRADE_CORRECT");
 }

 @Test void studentResultContainsOnlyOwnGpaChangeWithoutRankOrOtherStudents() throws Exception {
  Scenario s=scenario("student");var before=grades.preview(actor(s.admin),s.period.getId(),"BEFORE","帮扶前",sheet(s),request());
  grades.importBatch(actor(s.admin),before.batch().id(),new GradeImportRequest(before.suggestedMapping()),request());
  s.period.setStatus("ACCEPTANCE");periods.updateById(s.period);var after=grades.preview(actor(s.admin),s.period.getId(),"AFTER","帮扶后",sheetAfter(s),request());
  grades.importBatch(actor(s.admin),after.batch().id(),new GradeImportRequest(after.suggestedMapping()),request());
  var mine=grades.mine(s.navigator.getId());assertThat(mine).hasSize(1);assertThat(mine.getFirst().beforeTermGpa()).isEqualByComparingTo("3.20");
  assertThat(mine.getFirst().afterTermGpa()).isEqualByComparingTo("3.80");assertThat(mine.getFirst().change()).isEqualByComparingTo("0.60");
  assertThat(StudentGradeResultResponse.class.getRecordComponents()).extracting("name").doesNotContain("termGpaRank","professionalStudentCount","professionalTopPercent","studentNo","name");
 }

 @Test void moralFileRequiresPublishForStudentsAndSupportsWithdrawAndSoftDelete() {
  Scenario s=scenario("moral");var draft=moral.upload(actor(s.admin),s.period.getId(),"第二学期德育素质分",xlsxStub(),request());
  assertThat(moral.studentList(s.period.getId())).isEmpty();moral.publish(actor(s.admin),draft.id(),request());
  assertThat(moral.studentList(s.period.getId())).hasSize(1);assertThat(moral.download(draft.id(),false).resource().exists()).isTrue();
  moral.withdraw(actor(s.admin),draft.id(),request());assertThat(moral.studentList(s.period.getId())).isEmpty();
  assertError(()->moral.download(draft.id(),false),"MORAL_FILE_NOT_VISIBLE");moral.delete(actor(s.admin),draft.id(),request());
  assertThat(moral.adminList(s.period.getId())).isEmpty();assertThat(logs.selectList(Wrappers.emptyWrapper())).extracting("action").contains("MORAL_FILE_UPLOAD","MORAL_FILE_PUBLISH","MORAL_FILE_WITHDRAW","MORAL_FILE_DELETE");
 }

 @Test @WithMockUser(roles="STUDENT") void studentCannotAccessAdminRawGradeOrAdminMoralEndpoints() throws Exception {mvc.perform(get("/admin/grades")).andExpect(status().isForbidden());mvc.perform(get("/admin/moral-files")).andExpect(status().isForbidden());}
 @Test @WithMockUser(roles="ADMIN") void adminCannotUseStudentResultEndpoint() throws Exception {mvc.perform(get("/grades/mine")).andExpect(status().isForbidden());}

 private Scenario scenario(String x){UserAccount admin=user("w4-admin-"+x,"ADMIN","成果管理员"),nav=user("w4-nav-"+x,"STUDENT","领航者"),climb=user("w4-climb-"+x,"STUDENT","攀登者");
  ActivityPeriod p=new ActivityPeriod();p.setName("第四周测试-"+x);p.setShortName("W4-"+x);p.setStatus("TRACKING");LocalDateTime now=LocalDateTime.now();
  p.setRegistrationStart(now.minusDays(20));p.setRegistrationEnd(now.minusDays(19));p.setConfirmationDeadline(now.minusDays(18));p.setTrackingStart(now.minusDays(17));p.setTrackingEnd(now.plusDays(2));p.setAcceptanceStart(now.plusDays(3));p.setAcceptanceEnd(now.plusDays(10));p.setPhotoMin(1);p.setPhotoMax(2);p.setSubjectWeight(40);p.setMajorWeight(25);p.setGradeWeight(15);p.setLearningStyleWeight(10);p.setPersonalityWeight(5);p.setGpaGapWeight(5);p.setCreatedBy(admin.getId());periods.insert(p);
  String navNo="240001"+Math.abs(x.hashCode()%1000),climbNo="240002"+Math.abs(x.hashCode()%1000);ParticipationApplication na=application(p,nav,"NAVIGATOR",navNo),ca=application(p,climb,"CLIMBER",climbNo);
  SupportPair pair=new SupportPair();pair.setPeriodId(p.getId());pair.setPairNo("PAIR-W4-"+x);pair.setNavigatorApplicationId(na.getId());pair.setClimberApplicationId(ca.getId());pair.setStatus("ACTIVE");pair.setConfirmedAt(now.minusDays(18));pairs.insert(pair);return new Scenario(admin,nav,climb,p,navNo,climbNo);}
 private ParticipationApplication application(ActivityPeriod p,UserAccount u,String role,String no){ParticipationApplication a=new ParticipationApplication();a.setPeriodId(p.getId());a.setStudentUserId(u.getId());a.setParticipantRole(role);a.setCumulativeGpa(new BigDecimal("3.50"));a.setSubjectsJson("[]");a.setStatementText("第四周测试报名");a.setLearningStylesJson("[]");a.setPersonalitiesJson("[]");a.setApplicationStatus("APPROVED");a.setSelectionStatus("MATCHING_POOL");a.setSubmittedAt(LocalDateTime.now());applications.insert(a);ApplicationProfileSnapshot s=new ApplicationProfileSnapshot();s.setApplicationId(a.getId());s.setStudentUserId(u.getId());s.setProfileVersion(1);s.setStudentNo(no);s.setRealName(u.getDisplayName());s.setCollege("计算机学院");s.setMajor("软件工程");s.setGrade(2024);s.setWechat("test");snapshots.insert(s);return a;}
 private MockMultipartFile sheet(Scenario s) throws Exception{return workbook(s,"3.20","4.10");}private MockMultipartFile sheetAfter(Scenario s)throws Exception{return workbook(s,"3.80","4.30");}
 private MockMultipartFile workbook(Scenario s,String navGpa,String climbGpa)throws Exception{List<String[]> data=List.of(new String[]{s.navNo,"领航者","4.90","1",navGpa,"1","计算机学院","2024","软件工程","软工1班"},new String[]{s.climbNo,"攀登者","4.80","2",climbGpa,"2","计算机学院","2024","软件工程","软工2班"},new String[]{"999999","非参与学生","5.00","1","5.00","1","其他学院","2024","其他专业","1班"});return xlsx(data);}
 private MockMultipartFile xlsx(List<String[]> rows)throws Exception{try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()){var sh=wb.createSheet("成绩");String[] headers={"学号","姓名","平均绩点","平均绩点排名","平均学分绩点","平均学分绩点排名","学院","年级","专业","班级"};var hr=sh.createRow(0);for(int i=0;i<headers.length;i++)hr.createCell(i).setCellValue(headers[i]);for(int r=0;r<rows.size();r++){var row=sh.createRow(r+1);for(int c=0;c<headers.length;c++)row.createCell(c).setCellValue(rows.get(r)[c]);}wb.write(out);return new MockMultipartFile("file","grades.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());}}
 private MockMultipartFile xlsxStub(){try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var out=new java.io.ByteArrayOutputStream()){workbook.createSheet("德育分").createRow(0).createCell(0).setCellValue("学号");workbook.write(out);return new MockMultipartFile("file","moral.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());}catch(java.io.IOException e){throw new RuntimeException(e);}}
 private UserAccount user(String login,String type,String name){UserAccount u=new UserAccount();u.setLoginName(login);u.setPasswordHash(encoder.encode("WeekFour2026"));u.setAccountType(type);u.setDisplayName(name);u.setAccountStatus("ACTIVE");u.setTokenVersion(0);users.insert(u);return u;}
 private AuthenticatedUser actor(UserAccount u){return new AuthenticatedUser(u.getId(),u.getLoginName(),u.getDisplayName(),u.getAccountType(),0);}private MockHttpServletRequest request(){return new MockHttpServletRequest();}
 private void assertError(Runnable r,String code){BusinessException e=catchThrowableOfType(r::run,BusinessException.class);assertThat(e).isNotNull();assertThat(e.getCode()).isEqualTo(code);}
 private record Scenario(UserAccount admin,UserAccount navigator,UserAccount climber,ActivityPeriod period,String navNo,String climbNo){}
}
