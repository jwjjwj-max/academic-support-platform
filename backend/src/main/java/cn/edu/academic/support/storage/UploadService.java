package cn.edu.academic.support.storage;

import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.grade.service.GradeService;
import cn.edu.academic.support.material.service.MaterialService;
import cn.edu.academic.support.material.service.PrivateFileStorage;
import cn.edu.academic.support.moral.service.MoralFileService;
import cn.edu.academic.support.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

@Service
public class UploadService {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final ObjectStorage objects;
    private final PrivateFileStorage files; private final MaterialService materials; private final GradeService grades; private final MoralFileService moral;
    private final boolean enabled;
    public UploadService(JdbcTemplate jdbc,ObjectMapper json,ObjectStorage objects,PrivateFileStorage files,
                         MaterialService materials,GradeService grades,MoralFileService moral,
                         @Value("${app.storage.direct-upload:false}") boolean enabled) {
        this.jdbc=jdbc;this.json=json;this.objects=objects;this.files=files;this.materials=materials;this.grades=grades;this.moral=moral;
        this.enabled=enabled && objects instanceof CosObjectStorage;
    }
    public boolean enabled(){return enabled;}
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> begin(AuthenticatedUser actor,UploadRequest request) throws Exception {
        if(!enabled) throw new BusinessException("DIRECT_UPLOAD_DISABLED","当前使用站内上传");
        authorize(actor,request);
        long limit="PHOTO".equals(request.kind())?5242880:20971520;
        if(request.size()<1 || request.size()>limit) throw new BusinessException("FILE_SIZE_INVALID","文件为空或超过大小上限");
        if(request.filename()==null || request.filename().length()>255 || request.filename().contains("/") || request.filename().contains("\\") || request.filename().contains("\r") || request.filename().contains("\n"))
            throw new BusinessException("FILE_NAME_INVALID","文件名称无效");
        String extension=request.filename().substring(request.filename().lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        Set<String> extensions=switch(request.kind()){case "PHOTO"->Set.of("jpg","jpeg","png");case "GRADE"->Set.of("csv","xls","xlsx");default->Set.of("csv","xls","xlsx","pdf");};
        if(!extensions.contains(extension) || request.mimeType()==null || !request.mimeType().matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))
            throw new BusinessException("FILE_TYPE_INVALID","文件类型无效");
        Integer active=jdbc.queryForObject("SELECT COUNT(*) FROM storage_upload WHERE owner_id=? AND status='PENDING' AND expires_at>?",Integer.class,actor.id(),Timestamp.from(Instant.now()));
        if(active!=null && active>=5) throw new BusinessException("UPLOAD_LIMIT","请完成已有上传或稍后重试");
        String id=UUID.randomUUID().toString(),key="pending/"+id+"."+extension;
        jdbc.update("INSERT INTO storage_upload(id,owner_id,object_key,request_json,expires_at,status) VALUES(?,?,?,?,?,'PENDING')",id,actor.id(),key,json.writeValueAsString(request),Timestamp.from(Instant.now().plusSeconds(600)));
        return Map.of("id",id,"url",objects.signedPut(key,request.size(),request.mimeType()),"expiresIn",300);
    }
    private void authorize(AuthenticatedUser actor,UploadRequest r) {
        if("PHOTO".equals(r.kind())) {materials.authorizeUpload(actor,r.pairId(),r.photoId());return;}
        if(!"ADMIN".equals(actor.accountType())) throw new BusinessException("FORBIDDEN","需要管理员权限",HttpStatus.FORBIDDEN);
        if("GRADE".equals(r.kind())) grades.authorizeUpload(r.periodId(),r.stage(),r.label());
        else if("MORAL".equals(r.kind())) moral.authorizeUpload(r.periodId(),r.title());
        else throw new BusinessException("UPLOAD_KIND_INVALID","未知文件类别");
    }
    @Transactional(rollbackFor=Exception.class)
    public JsonNode finish(AuthenticatedUser actor,String id,HttpServletRequest http) throws Exception {
        var rows=jdbc.queryForList("SELECT * FROM storage_upload WHERE id=? AND owner_id=? FOR UPDATE",id,actor.id());
        if(rows.isEmpty()) throw new BusinessException("UPLOAD_NOT_FOUND","上传不存在",HttpStatus.NOT_FOUND);
        var row=rows.getFirst();
        if("DONE".equals(row.get("status"))) return json.readTree((String)row.get("result_json"));
        if(((Timestamp)row.get("expires_at")).toInstant().isBefore(Instant.now())) throw new BusinessException("UPLOAD_EXPIRED","上传已过期，请重新选择文件");
        UploadRequest request=json.readValue((String)row.get("request_json"),UploadRequest.class);authorize(actor,request);
        String key=(String)row.get("object_key");
        if(objects.size(key)!=request.size()) throw new BusinessException("UPLOAD_SIZE_MISMATCH","上传文件大小不一致");
        byte[] bytes=files.read(key); // read the actual object; the signed temporary object is never a business reference
        MultipartResult result=create(actor,request,new StoredMultipartFile(request,bytes),http);
        JsonNode response=json.valueToTree(result.value());
        jdbc.update("UPDATE storage_upload SET status='DONE',result_json=? WHERE id=?",json.writeValueAsString(response),id);
        return response;
    }
    private MultipartResult create(AuthenticatedUser actor,UploadRequest r,StoredMultipartFile file,HttpServletRequest http) {
        return new MultipartResult(switch(r.kind()) {
            case "PHOTO" -> r.photoId()==null ? materials.uploadPhoto(actor,r.pairId(),file,r.description(),http) : materials.resubmitPhoto(actor,r.photoId(),file,r.description(),http);
            case "GRADE" -> grades.preview(actor,r.periodId(),r.stage(),r.label(),file,http);
            case "MORAL" -> moral.upload(actor,r.periodId(),r.title(),file,http);
            default -> throw new IllegalStateException("Unknown upload kind");
        });
    }
    private record MultipartResult(Object value){}
}
