package cn.edu.academic.support.grade.controller;

import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.grade.dto.*;
import cn.edu.academic.support.grade.service.GradeService;
import cn.edu.academic.support.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequiredArgsConstructor
public class GradeController {
    private final GradeService service;

    @PostMapping(value="/admin/grades/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GradePreviewResponse> preview(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam Long periodId,
      @RequestParam String stage,@RequestParam String label,@RequestPart MultipartFile file,HttpServletRequest request){return ApiResponse.success(service.preview(actor,periodId,stage,label,file,request));}

    @PostMapping("/admin/grades/batches/{id}/import") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GradeBatchResponse> importBatch(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,
      @Valid @RequestBody GradeImportRequest body,HttpServletRequest request){return ApiResponse.success(service.importBatch(actor,id,body,request));}

    @GetMapping("/admin/grades/batches") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<GradeBatchResponse>> batches(@RequestParam(required=false) Long periodId){return ApiResponse.success(service.batches(periodId));}

    @GetMapping("/admin/grades") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<AdminGradeResponse>> grades(@RequestParam(required=false) Long periodId){return ApiResponse.success(service.adminGrades(periodId));}

    @GetMapping("/admin/grades/cohort-stats") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<GradeCohortStatResponse>> cohortStats(@RequestParam(required=false) Long periodId){return ApiResponse.success(service.cohortStats(periodId));}

    @PatchMapping("/admin/grades/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminGradeResponse> correct(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,
      @Valid @RequestBody GradeCorrectionRequest body,HttpServletRequest request){return ApiResponse.success(service.correct(actor,id,body,request));}

    @GetMapping("/admin/grades/batches/{id}/file/access") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> access(@PathVariable Long id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(ApiResponse.success(service.accessUrl(id)));
    }
    @GetMapping("/admin/grades/batches/{id}/file") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> raw(@PathVariable Long id){var d=service.downloadBatch(id);return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(d.mimeType())).contentLength(d.size()).header(HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename(d.filename(),StandardCharsets.UTF_8).build().toString()).header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(d.resource());}

    @GetMapping("/grades/mine") @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<StudentGradeResultResponse>> mine(@AuthenticationPrincipal AuthenticatedUser actor){return ApiResponse.success(service.mine(actor.id()));}
}
