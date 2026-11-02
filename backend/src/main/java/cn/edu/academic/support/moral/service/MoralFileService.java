package cn.edu.academic.support.moral.service;

import cn.edu.academic.support.audit.service.AuditService;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.material.service.PrivateFileStorage;
import cn.edu.academic.support.moral.dto.MoralFileResponse;
import cn.edu.academic.support.moral.mapper.MoralScoreFileMapper;
import cn.edu.academic.support.moral.model.MoralScoreFile;
import cn.edu.academic.support.period.mapper.ActivityPeriodMapper;
import cn.edu.academic.support.period.model.ActivityPeriod;
import cn.edu.academic.support.security.AuthenticatedUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class MoralFileService {
 private final MoralScoreFileMapper mapper; private final ActivityPeriodMapper periodMapper; private final PrivateFileStorage storage; private final AuditService audit;
 @Transactional public MoralFileResponse upload(AuthenticatedUser actor,Long periodId,String title,MultipartFile file,HttpServletRequest request){
  ActivityPeriod p=requirePeriod(periodId);if(title==null||title.isBlank()||title.trim().length()>200)throw new BusinessException("MORAL_TITLE_INVALID","请输入200字以内的文件标题");
  var stored=storage.storeMoral(file);MoralScoreFile e=new MoralScoreFile();e.setPeriodId(periodId);e.setTitle(title.trim());e.setStorageKey(stored.key());
  e.setOriginalFilename(stored.originalFilename());e.setMimeType(stored.mimeType());e.setFileSize(stored.size());e.setStatus("DRAFT");e.setUploadedBy(actor.id());e.setDeleted(false);mapper.insert(e);
  audit.log(actor,"MORAL_FILE_UPLOAD","MORAL_SCORE_FILE",e.getId(),null,Map.of("title",e.getTitle(),"status","DRAFT"),null,request);return response(e,p,true);
 }
 public List<MoralFileResponse> adminList(Long periodId){return mapper.selectList(Wrappers.<MoralScoreFile>lambdaQuery().eq(periodId!=null,MoralScoreFile::getPeriodId,periodId).orderByDesc(MoralScoreFile::getCreatedAt)).stream().map(e->response(e,requirePeriod(e.getPeriodId()),true)).toList();}
 public List<MoralFileResponse> studentList(Long periodId){return mapper.selectList(Wrappers.<MoralScoreFile>lambdaQuery().eq(periodId!=null,MoralScoreFile::getPeriodId,periodId).eq(MoralScoreFile::getStatus,"PUBLISHED").orderByDesc(MoralScoreFile::getPublishedAt)).stream().map(e->response(e,requirePeriod(e.getPeriodId()),false)).toList();}
 @Transactional public MoralFileResponse publish(AuthenticatedUser actor,Long id,HttpServletRequest request){MoralScoreFile e=require(id);if(!List.of("DRAFT","WITHDRAWN").contains(e.getStatus()))throw new BusinessException("MORAL_FILE_NOT_PUBLISHABLE","当前文件不可发布");String old=e.getStatus();e.setStatus("PUBLISHED");e.setPublishedBy(actor.id());e.setPublishedAt(LocalDateTime.now());e.setWithdrawnAt(null);e.setWithdrawnBy(null);mapper.updateById(e);audit.log(actor,"MORAL_FILE_PUBLISH","MORAL_SCORE_FILE",id,Map.of("status",old),Map.of("status","PUBLISHED"),null,request);return response(e,requirePeriod(e.getPeriodId()),true);}
 @Transactional public MoralFileResponse withdraw(AuthenticatedUser actor,Long id,HttpServletRequest request){MoralScoreFile e=require(id);if(!"PUBLISHED".equals(e.getStatus()))throw new BusinessException("MORAL_FILE_NOT_PUBLISHED","仅已发布文件可撤回");e.setStatus("WITHDRAWN");e.setWithdrawnAt(LocalDateTime.now());e.setWithdrawnBy(actor.id());mapper.updateById(e);audit.log(actor,"MORAL_FILE_WITHDRAW","MORAL_SCORE_FILE",id,Map.of("status","PUBLISHED"),Map.of("status","WITHDRAWN"),null,request);return response(e,requirePeriod(e.getPeriodId()),true);}
 @Transactional public void delete(AuthenticatedUser actor,Long id,HttpServletRequest request){MoralScoreFile e=require(id);if("PUBLISHED".equals(e.getStatus()))throw new BusinessException("MORAL_FILE_DELETE_PUBLISHED","请先撤回已发布文件再删除");e.setDeletedBy(actor.id());e.setDeletedAt(LocalDateTime.now());mapper.updateById(e);mapper.deleteById(e);audit.log(actor,"MORAL_FILE_DELETE","MORAL_SCORE_FILE",id,Map.of("status",e.getStatus()),Map.of("deleted",true),null,request);}
 public void authorizeUpload(Long periodId,String title){requirePeriod(periodId);if(title==null||title.isBlank()||title.trim().length()>200)throw new BusinessException("MORAL_TITLE_INVALID","请输入200字以内的文件标题");}
 public String accessUrl(Long id,boolean admin){MoralScoreFile e=require(id);if(!admin&&!"PUBLISHED".equals(e.getStatus()))throw new BusinessException("MORAL_FILE_NOT_VISIBLE","文件尚未发布",HttpStatus.FORBIDDEN);String url=storage.signedGet(e.getStorageKey());return url==null?(admin?"/api/admin/moral-files/":"/api/moral-files/")+id+"/file":url;}
 public Download download(Long id,boolean admin){MoralScoreFile e=require(id);if(!admin&&!"PUBLISHED".equals(e.getStatus()))throw new BusinessException("MORAL_FILE_NOT_VISIBLE","文件尚未发布",HttpStatus.FORBIDDEN);return new Download(storage.resource(e.getStorageKey()),e.getOriginalFilename(),e.getMimeType(),e.getFileSize());}
 private MoralScoreFile require(Long id){MoralScoreFile e=mapper.selectById(id);if(e==null)throw new BusinessException("MORAL_FILE_NOT_FOUND","德育素质分文件不存在",HttpStatus.NOT_FOUND);return e;}
 private ActivityPeriod requirePeriod(Long id){ActivityPeriod p=periodMapper.selectById(id);if(p==null)throw new BusinessException("PERIOD_NOT_FOUND","期次不存在",HttpStatus.NOT_FOUND);return p;}
 private MoralFileResponse response(MoralScoreFile e,ActivityPeriod p,boolean admin){return new MoralFileResponse(e.getId(),e.getPeriodId(),p.getName(),e.getTitle(),e.getOriginalFilename(),e.getMimeType(),e.getFileSize(),e.getStatus(),e.getCreatedAt(),e.getPublishedAt(),e.getWithdrawnAt(),(admin?"/api/admin/moral-files/":"/api/moral-files/")+e.getId()+"/file");}
 public record Download(Resource resource,String filename,String mimeType,long size){}
}
