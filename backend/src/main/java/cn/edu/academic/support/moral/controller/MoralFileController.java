package cn.edu.academic.support.moral.controller;
import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.moral.dto.MoralFileResponse;
import cn.edu.academic.support.moral.service.MoralFileService;
import cn.edu.academic.support.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
@RestController @RequiredArgsConstructor public class MoralFileController {
 private final MoralFileService service;
 @PostMapping(value="/admin/moral-files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('ADMIN')") public ApiResponse<MoralFileResponse> upload(@AuthenticationPrincipal AuthenticatedUser a,@RequestParam Long periodId,@RequestParam String title,@RequestPart MultipartFile file,HttpServletRequest r){return ApiResponse.success(service.upload(a,periodId,title,file,r));}
 @GetMapping("/admin/moral-files") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<List<MoralFileResponse>> admin(@RequestParam(required=false)Long periodId){return ApiResponse.success(service.adminList(periodId));}
 @PostMapping("/admin/moral-files/{id}/publish") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<MoralFileResponse> publish(@AuthenticationPrincipal AuthenticatedUser a,@PathVariable Long id,HttpServletRequest r){return ApiResponse.success(service.publish(a,id,r));}
 @PostMapping("/admin/moral-files/{id}/withdraw") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<MoralFileResponse> withdraw(@AuthenticationPrincipal AuthenticatedUser a,@PathVariable Long id,HttpServletRequest r){return ApiResponse.success(service.withdraw(a,id,r));}
 @DeleteMapping("/admin/moral-files/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser a,@PathVariable Long id,HttpServletRequest r){service.delete(a,id,r);return ApiResponse.success(null);}
 @GetMapping("/moral-files") @PreAuthorize("hasRole('STUDENT')") public ApiResponse<List<MoralFileResponse>> list(@RequestParam(required=false)Long periodId){return ApiResponse.success(service.studentList(periodId));}
 @GetMapping("/admin/moral-files/{id}/file/access") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<ApiResponse<String>> adminAccess(@PathVariable Long id){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(ApiResponse.success(service.accessUrl(id,true)));}
 @GetMapping("/moral-files/{id}/file/access") @PreAuthorize("hasRole('STUDENT')") public ResponseEntity<ApiResponse<String>> access(@PathVariable Long id){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(ApiResponse.success(service.accessUrl(id,false)));}
 @GetMapping("/admin/moral-files/{id}/file") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<Resource> adminFile(@PathVariable Long id){return download(service.download(id,true));}
 @GetMapping("/moral-files/{id}/file") @PreAuthorize("hasRole('STUDENT')") public ResponseEntity<Resource> file(@PathVariable Long id){return download(service.download(id,false));}
 private ResponseEntity<Resource> download(MoralFileService.Download d){return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.mimeType())).contentLength(d.size()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.filename(),StandardCharsets.UTF_8).build().toString()).header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(d.resource());}
}
