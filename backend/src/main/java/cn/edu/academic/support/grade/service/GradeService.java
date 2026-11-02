package cn.edu.academic.support.grade.service;

import cn.edu.academic.support.application.mapper.*;
import cn.edu.academic.support.application.model.*;
import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.grade.dto.*;
import cn.edu.academic.support.grade.mapper.*;
import cn.edu.academic.support.grade.model.*;
import cn.edu.academic.support.matching.mapper.SupportPairMapper;
import cn.edu.academic.support.matching.model.SupportPair;
import cn.edu.academic.support.material.service.PrivateFileStorage;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.security.AuthenticatedUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class GradeService {
    private static final Set<String> STAGES=Set.of("BEFORE","AFTER");
    private final GradeImportBatchMapper batchMapper; private final ParticipantTermGradeMapper gradeMapper;
    private final GradeCohortStatMapper cohortStatMapper;
    private final GradeCorrectionHistoryMapper correctionMapper; private final ActivityPeriodMapper periodMapper;
    private final SupportPairMapper pairMapper; private final ParticipationApplicationMapper applicationMapper;
    private final ApplicationProfileSnapshotMapper snapshotMapper; private final PrivateFileStorage storage;
    private final GradeSheetReader reader; private final AuditService audit; private final ObjectMapper json;

    @Transactional
    public GradePreviewResponse preview(AuthenticatedUser actor, Long periodId, String stage, String label,
                                        MultipartFile file, HttpServletRequest request) {
        ActivityPeriod period=requirePeriod(periodId); stage=normalizeStage(stage); requireStage(period,stage);
        if (label==null || label.isBlank() || label.trim().length()>100) throw new BusinessException("GRADE_LABEL_INVALID","请输入100字以内的成绩单标签");
        var stored=storage.storeGrade(file); var data=reader.read(storage.read(stored.key()),stored.originalFilename());
        Map<String,String> suggested=suggest(data.headers()); Map<String,Participant> participants=participants(periodId);
        int participantRows=countParticipants(data,suggested.get("studentNo"),participants.keySet());
        GradeImportBatch batch=new GradeImportBatch(); batch.setPeriodId(periodId);batch.setStage(stage);batch.setLabel(label.trim());
        batch.setStorageKey(stored.key());batch.setOriginalFilename(stored.originalFilename());batch.setMimeType(stored.mimeType());batch.setFileSize(stored.size());
        batch.setFieldMapping(writeJson(suggested));batch.setStatus("PREVIEWED");batch.setTotalRows(data.rows().size());
        batch.setParticipantRows(participantRows);batch.setErrorRows(0);batch.setUploadedBy(actor.id());batchMapper.insert(batch);
        audit.log(actor,"GRADE_PREVIEW","GRADE_IMPORT_BATCH",batch.getId(),null,Map.of("periodId",periodId,"stage",stage,"totalRows",data.rows().size(),"participantRows",participantRows),null,request);
        List<Map<String,String>> samples=data.rows().stream().limit(5).map(GradeSheetReader.SheetRow::values).toList();
        List<String> messages=new ArrayList<>();
        if (!suggested.containsKey("studentNo")) messages.add("未自动识别“学号”列，请手动映射。");
        if (!suggested.containsKey("termGpa")) messages.add("未自动识别“平均学分绩点”列，请手动映射；不得选择“平均绩点”。");
        if (!suggested.containsKey("termGpaRank")) messages.add("未自动识别“平均学分绩点排名”列，请手动映射。");
        if (!suggested.containsKey("grade")) messages.add("未自动识别“年级”列，请手动映射。");
        if (!suggested.containsKey("major")) messages.add("未自动识别“专业”列，请手动映射。");
        if (participantRows<participants.size()) messages.add("预览中识别到"+participantRows+"名参与者，本期正式参与者共"+participants.size()+"名。");
        return new GradePreviewResponse(batchResponse(batch,period),data.headers(),suggested,samples,messages,participants.size());
    }

    @Transactional
    public GradeBatchResponse importBatch(AuthenticatedUser actor,Long batchId,GradeImportRequest body,HttpServletRequest request) {
        GradeImportBatch batch=requireBatch(batchId); ActivityPeriod period=requirePeriod(batch.getPeriodId()); requireStage(period,batch.getStage());
        if (!"PREVIEWED".equals(batch.getStatus())) throw new BusinessException("GRADE_BATCH_ALREADY_IMPORTED","该成绩单批次已经导入，请重新上传后再操作");
        var data=reader.read(storage.read(batch.getStorageKey()),batch.getOriginalFilename());
        Map<String,String> mapping=validateMapping(body.mapping(),data.headers()); Map<String,Participant> participants=participants(batch.getPeriodId());
        Map<CohortKey,Integer> cohortCounts=new LinkedHashMap<>(); Set<String> sourceStudentNos=new HashSet<>();
        Map<String,ParsedGrade> parsed=new LinkedHashMap<>(); List<String> errors=new ArrayList<>();
        for (var row:data.rows()) {
            String no=value(row,mapping,"studentNo"), grade=value(row,mapping,"grade"), major=value(row,mapping,"major");
            if (!sourceStudentNos.add(no)) errors.add("第"+row.rowNo()+"行：学号"+no+"重复");
            cohortCounts.merge(new CohortKey(grade,major),1,Integer::sum);
        }
        for (var row:data.rows()) {
            String no=value(row,mapping,"studentNo"); if (!participants.containsKey(no)) continue;
            if (parsed.containsKey(no)) { errors.add("第"+row.rowNo()+"行：参与者学号"+no+"重复"); continue; }
            try {
                BigDecimal gpa=new BigDecimal(value(row,mapping,"termGpa")).setScale(2,RoundingMode.HALF_UP);
                if (gpa.compareTo(BigDecimal.ZERO)<0 || gpa.compareTo(new BigDecimal("5.00"))>0) throw new NumberFormatException();
                String grade=value(row,mapping,"grade"), major=value(row,mapping,"major");
                Integer rank=Integer.valueOf(value(row,mapping,"termGpaRank").replace(",",""));
                int professionalCount=cohortCounts.getOrDefault(new CohortKey(grade,major),0);
                if (rank<=0 || professionalCount<=0 || rank>professionalCount) throw new NumberFormatException();
                parsed.put(no,new ParsedGrade(row,gpa,rank,professionalCount));
            } catch (Exception e) { errors.add("第"+row.rowNo()+"行：参与者"+no+"的平均学分绩点或排名无效"); }
        }
        Set<String> missing=new LinkedHashSet<>(participants.keySet());missing.removeAll(parsed.keySet());
        if (!missing.isEmpty()) errors.add("成绩单缺少"+missing.size()+"名本期参与者："+String.join("、",missing.stream().limit(5).toList())+(missing.size()>5?"等":""));
        if (!errors.isEmpty()) throw new BusinessException("GRADE_IMPORT_VALIDATION_FAILED",String.join("；",errors.stream().limit(8).toList()));
        for (var entry:cohortCounts.entrySet()) {
            GradeCohortStat stat=new GradeCohortStat();stat.setImportBatchId(batchId);stat.setPeriodId(batch.getPeriodId());stat.setStage(batch.getStage());
            stat.setGrade(entry.getKey().grade());stat.setMajor(entry.getKey().major());stat.setStudentCount(entry.getValue());cohortStatMapper.insert(stat);
        }
        for (var entry:parsed.entrySet()) {
            Participant p=participants.get(entry.getKey()); ParsedGrade g=entry.getValue();
            ParticipantTermGrade entity=gradeMapper.selectOne(Wrappers.<ParticipantTermGrade>lambdaQuery()
                    .eq(ParticipantTermGrade::getPeriodId,batch.getPeriodId()).eq(ParticipantTermGrade::getStudentUserId,p.application().getStudentUserId())
                    .eq(ParticipantTermGrade::getStage,batch.getStage()));
            boolean existing=entity!=null;if(!existing)entity=new ParticipantTermGrade();
            entity.setPeriodId(batch.getPeriodId());entity.setStudentUserId(p.application().getStudentUserId());entity.setApplicationId(p.application().getId());
            entity.setStage(batch.getStage());entity.setImportBatchId(batchId);entity.setTermGpa(g.gpa());entity.setTermGpaRank(g.rank());entity.setProfessionalStudentCount(g.professionalCount());
            entity.setStudentNoSnapshot(p.snapshot().getStudentNo());entity.setNameSnapshot(optional(g.row(),mapping,"name",p.snapshot().getRealName()));
            entity.setCollegeSnapshot(optional(g.row(),mapping,"college",p.snapshot().getCollege()));entity.setGradeSnapshot(optional(g.row(),mapping,"grade",String.valueOf(p.snapshot().getGrade())));
            entity.setMajorSnapshot(optional(g.row(),mapping,"major",p.snapshot().getMajor()));entity.setClassNameSnapshot(optional(g.row(),mapping,"className",null));
            if(existing)gradeMapper.updateById(entity);else gradeMapper.insert(entity);
        }
        batch.setFieldMapping(writeJson(mapping));batch.setStatus("IMPORTED");batch.setParticipantRows(parsed.size());batch.setErrorRows(0);batch.setCompletedAt(LocalDateTime.now());batchMapper.updateById(batch);
        audit.log(actor,"GRADE_IMPORT","GRADE_IMPORT_BATCH",batchId,null,Map.of("periodId",batch.getPeriodId(),"stage",batch.getStage(),"participantRows",parsed.size(),"cohortGroups",cohortCounts.size()),null,request);
        return batchResponse(batch,period);
    }

    public List<GradeBatchResponse> batches(Long periodId) { return batchMapper.selectList(Wrappers.<GradeImportBatch>lambdaQuery()
            .eq(periodId!=null,GradeImportBatch::getPeriodId,periodId).orderByDesc(GradeImportBatch::getCreatedAt)).stream()
            .map(b->batchResponse(b,requirePeriod(b.getPeriodId()))).toList(); }

    public List<AdminGradeResponse> adminGrades(Long periodId) {
        return gradeMapper.selectList(Wrappers.<ParticipantTermGrade>lambdaQuery().eq(periodId!=null,ParticipantTermGrade::getPeriodId,periodId)
                .orderByAsc(ParticipantTermGrade::getStudentNoSnapshot).orderByAsc(ParticipantTermGrade::getStage)).stream().map(this::adminResponse).toList();
    }

    public List<GradeCohortStatResponse> cohortStats(Long periodId) {
        List<GradeImportBatch> imported=batchMapper.selectList(Wrappers.<GradeImportBatch>lambdaQuery()
                .eq(periodId!=null,GradeImportBatch::getPeriodId,periodId).eq(GradeImportBatch::getStatus,"IMPORTED")
                .orderByDesc(GradeImportBatch::getCompletedAt).orderByDesc(GradeImportBatch::getId));
        Map<String,GradeImportBatch> latest=new LinkedHashMap<>();
        for (GradeImportBatch batch:imported) latest.putIfAbsent(batch.getPeriodId()+":"+batch.getStage(),batch);
        if (latest.isEmpty()) return List.of();
        Map<Long,GradeImportBatch> batches=latest.values().stream().collect(Collectors.toMap(GradeImportBatch::getId,Function.identity()));
        return cohortStatMapper.selectList(Wrappers.<GradeCohortStat>lambdaQuery().in(GradeCohortStat::getImportBatchId,batches.keySet()))
                .stream().map(stat->{GradeImportBatch batch=batches.get(stat.getImportBatchId());ActivityPeriod period=requirePeriod(stat.getPeriodId());
                    return new GradeCohortStatResponse(stat.getId(),stat.getPeriodId(),period.getName(),stat.getStage(),stat.getGrade(),stat.getMajor(),
                            stat.getStudentCount(),stat.getImportBatchId(),batch.getCompletedAt());})
                .sorted(Comparator.comparing(GradeCohortStatResponse::periodId).reversed().thenComparing(GradeCohortStatResponse::grade)
                        .thenComparing(GradeCohortStatResponse::major).thenComparing(GradeCohortStatResponse::stage)).toList();
    }

    @Transactional public AdminGradeResponse correct(AuthenticatedUser actor,Long gradeId,GradeCorrectionRequest body,HttpServletRequest request) {
        ParticipantTermGrade grade=gradeMapper.selectById(gradeId);if(grade==null)throw new BusinessException("GRADE_NOT_FOUND","成绩记录不存在",HttpStatus.NOT_FOUND);
        requirePeriod(grade.getPeriodId()); GradeCorrectionHistory history=new GradeCorrectionHistory();history.setGradeId(gradeId);
        if (body.termGpaRank()!=null && grade.getProfessionalStudentCount()!=null && body.termGpaRank()>grade.getProfessionalStudentCount())
            throw new BusinessException("GRADE_RANK_EXCEEDS_COHORT","专业排名不能超过该年级专业人数"+grade.getProfessionalStudentCount());
        history.setOldTermGpa(grade.getTermGpa());history.setNewTermGpa(body.termGpa().setScale(2,RoundingMode.HALF_UP));
        history.setOldTermGpaRank(grade.getTermGpaRank());history.setNewTermGpaRank(body.termGpaRank());history.setReason(body.reason().trim());history.setOperatorId(actor.id());correctionMapper.insert(history);
        Map<String,Object> before=new LinkedHashMap<>();before.put("termGpa",grade.getTermGpa());before.put("termGpaRank",grade.getTermGpaRank());
        grade.setTermGpa(history.getNewTermGpa());grade.setTermGpaRank(body.termGpaRank());gradeMapper.updateById(grade);
        audit.log(actor,"GRADE_CORRECT","PARTICIPANT_TERM_GRADE",gradeId,before,Map.of("termGpa",grade.getTermGpa(),"termGpaRank",Objects.toString(grade.getTermGpaRank(),"")),body.reason().trim(),request);
        return adminResponse(grade);
    }

    public List<StudentGradeResultResponse> mine(Long userId) {
        List<ParticipantTermGrade> rows=gradeMapper.selectList(Wrappers.<ParticipantTermGrade>lambdaQuery().eq(ParticipantTermGrade::getStudentUserId,userId));
        return rows.stream().collect(Collectors.groupingBy(ParticipantTermGrade::getPeriodId)).entrySet().stream().map(e->{
            BigDecimal before=e.getValue().stream().filter(g->"BEFORE".equals(g.getStage())).map(ParticipantTermGrade::getTermGpa).findFirst().orElse(null);
            BigDecimal after=e.getValue().stream().filter(g->"AFTER".equals(g.getStage())).map(ParticipantTermGrade::getTermGpa).findFirst().orElse(null);
            ParticipationApplication app=applicationMapper.selectById(e.getValue().getFirst().getApplicationId());
            return new StudentGradeResultResponse(e.getKey(),requirePeriod(e.getKey()).getName(),app.getParticipantRole(),before,after,before!=null&&after!=null?after.subtract(before).setScale(2):null);
        }).sorted(Comparator.comparing(StudentGradeResultResponse::periodId).reversed()).toList();
    }

    public void authorizeUpload(Long periodId,String stage,String label) {
        requireStage(requirePeriod(periodId),normalizeStage(stage));
        if(label==null || label.isBlank() || label.trim().length()>100) throw new BusinessException("GRADE_LABEL_INVALID","请输入100字以内的成绩单标签");
    }
    public String accessUrl(Long id) {
        String url=storage.signedGet(requireBatch(id).getStorageKey());
        return url==null ? "/api/admin/grades/batches/"+id+"/file" : url;
    }
    public Download downloadBatch(Long batchId) { GradeImportBatch b=requireBatch(batchId);return new Download(storage.resource(b.getStorageKey()),b.getOriginalFilename(),b.getMimeType(),b.getFileSize()); }

    private Map<String,Participant> participants(Long periodId) {
        List<SupportPair> pairs=pairMapper.selectList(Wrappers.<SupportPair>lambdaQuery().eq(SupportPair::getPeriodId,periodId));
        Set<Long> ids=pairs.stream().flatMap(p->java.util.stream.Stream.of(p.getNavigatorApplicationId(),p.getClimberApplicationId())).collect(Collectors.toSet());
        if(ids.isEmpty())throw new BusinessException("GRADE_NO_PARTICIPANTS","本期尚无正式帮扶参与者");
        Map<Long,ParticipationApplication> apps=applicationMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(ParticipationApplication::getId,Function.identity()));
        Map<Long,ApplicationProfileSnapshot> snapshots=snapshotMapper.selectList(Wrappers.<ApplicationProfileSnapshot>lambdaQuery().in(ApplicationProfileSnapshot::getApplicationId,ids)).stream().collect(Collectors.toMap(ApplicationProfileSnapshot::getApplicationId,Function.identity()));
        Map<String,Participant> result=new LinkedHashMap<>();for(Long id:ids){var a=apps.get(id);var s=snapshots.get(id);if(a==null||s==null)throw new BusinessException("GRADE_PARTICIPANT_SNAPSHOT_MISSING","参与者报名快照不完整");result.put(normalizeNo(s.getStudentNo()),new Participant(a,s));}return result;
    }
    private Map<String,String> suggest(List<String> h){Map<String,String> m=new LinkedHashMap<>();putExact(m,h,"studentNo","学号");putExact(m,h,"name","姓名");putExact(m,h,"termGpa","平均学分绩点");putExact(m,h,"termGpaRank","平均学分绩点排名");putExact(m,h,"college","学院");putExact(m,h,"grade","年级");putExact(m,h,"major","专业");putExact(m,h,"className","班级");return m;}
    private void putExact(Map<String,String> m,List<String> h,String k,String expected){h.stream().filter(x->x.trim().equals(expected)).findFirst().ifPresent(x->m.put(k,x));}
    private Map<String,String> validateMapping(Map<String,String> input,List<String> headers){Map<String,String> m=new LinkedHashMap<>(input);Set<String> required=Set.of("studentNo","termGpa","termGpaRank","grade","major");if(!m.keySet().containsAll(required)||required.stream().anyMatch(k->m.get(k)==null||m.get(k).isBlank()))throw new BusinessException("GRADE_MAPPING_REQUIRED","必须映射学号、年级、专业、平均学分绩点和平均学分绩点排名");if(new HashSet<>(m.values()).size()!=m.values().size())throw new BusinessException("GRADE_MAPPING_DUPLICATE","同一成绩单列不能映射到多个字段");for(String v:m.values())if(!headers.contains(v))throw new BusinessException("GRADE_MAPPING_HEADER_MISSING","映射列不存在："+v);if("平均绩点".equals(m.get("termGpa"))||"平均绩点排名".equals(m.get("termGpaRank")))throw new BusinessException("GRADE_MAPPING_WRONG_GPA","不得将“平均绩点”映射为“平均学分绩点”");return m;}
    private int countParticipants(GradeSheetReader.SheetData d,String column,Set<String> nos){if(column==null)return 0;return(int)d.rows().stream().map(r->normalizeNo(r.values().get(column))).filter(nos::contains).distinct().count();}
    private String value(GradeSheetReader.SheetRow r,Map<String,String> m,String key){String v=optional(r,m,key);if(v.isBlank())throw new BusinessException("GRADE_CELL_REQUIRED","第"+r.rowNo()+"行缺少"+key);return "studentNo".equals(key)?normalizeNo(v):v;}
    private String optional(GradeSheetReader.SheetRow r,Map<String,String> m,String key){String col=m.get(key);return col==null?"":Objects.toString(r.values().get(col),"").trim();}
    private String optional(GradeSheetReader.SheetRow r,Map<String,String> m,String key,String fallback){String v=optional(r,m,key);return v.isBlank()?fallback:v;}
    private String normalizeNo(String value){if(value==null)return"";String v=value.trim();return v.endsWith(".0")?v.substring(0,v.length()-2):v;}
    private String normalizeStage(String s){String v=s==null?"":s.trim().toUpperCase(Locale.ROOT);if(!STAGES.contains(v))throw new BusinessException("GRADE_STAGE_INVALID","成绩阶段仅支持帮扶前或帮扶后");return v;}
    private void requireStage(ActivityPeriod p,String stage){List<String> allowed="BEFORE".equals(stage)?List.of("TRACKING","ACCEPTANCE","ENDED","ARCHIVED"):List.of("ACCEPTANCE","ENDED","ARCHIVED");if(!allowed.contains(p.getStatus()))throw new BusinessException("GRADE_PERIOD_STAGE_INVALID","当前期次阶段不可导入该成绩单");}
    private ActivityPeriod requirePeriod(Long id){ActivityPeriod p=periodMapper.selectById(id);if(p==null)throw new BusinessException("PERIOD_NOT_FOUND","期次不存在",HttpStatus.NOT_FOUND);return p;}
    private GradeImportBatch requireBatch(Long id){GradeImportBatch b=batchMapper.selectById(id);if(b==null)throw new BusinessException("GRADE_BATCH_NOT_FOUND","成绩导入批次不存在",HttpStatus.NOT_FOUND);return b;}
    private GradeBatchResponse batchResponse(GradeImportBatch b,ActivityPeriod p){return new GradeBatchResponse(b.getId(),b.getPeriodId(),p.getName(),b.getStage(),b.getLabel(),b.getOriginalFilename(),b.getStatus(),b.getTotalRows(),b.getParticipantRows(),b.getErrorRows(),readJson(b.getFieldMapping()),b.getCreatedAt(),b.getCompletedAt());}
    private AdminGradeResponse adminResponse(ParticipantTermGrade g){ActivityPeriod p=requirePeriod(g.getPeriodId());ParticipationApplication a=applicationMapper.selectById(g.getApplicationId());return new AdminGradeResponse(g.getId(),g.getPeriodId(),p.getName(),g.getStudentUserId(),g.getStudentNoSnapshot(),g.getNameSnapshot(),a.getParticipantRole(),g.getStage(),g.getTermGpa(),g.getTermGpaRank(),g.getProfessionalStudentCount(),topPercent(g.getTermGpaRank(),g.getProfessionalStudentCount()),g.getCollegeSnapshot(),g.getGradeSnapshot(),g.getMajorSnapshot(),g.getClassNameSnapshot(),g.getImportBatchId(),g.getUpdatedAt());}
    private BigDecimal topPercent(Integer rank,Integer count){return rank==null||count==null||count<=0?null:BigDecimal.valueOf(rank).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(count),1,RoundingMode.HALF_UP);}
    private String writeJson(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,String> readJson(String s){try{return json.readValue(s,new TypeReference<>(){});}catch(Exception e){return Map.of();}}
    private record Participant(ParticipationApplication application,ApplicationProfileSnapshot snapshot){} private record CohortKey(String grade,String major){} private record ParsedGrade(GradeSheetReader.SheetRow row,BigDecimal gpa,Integer rank,Integer professionalCount){}
    public record Download(Resource resource,String filename,String mimeType,long size){}
}
