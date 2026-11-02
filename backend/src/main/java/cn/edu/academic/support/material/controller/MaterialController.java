package cn.edu.academic.support.material.controller;

import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.material.dto.*;
import cn.edu.academic.support.material.service.MaterialService;
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

@RestController
@RequiredArgsConstructor
public class MaterialController {
    private final MaterialService service;

    @GetMapping("/materials/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<PairDetailResponse>> mine(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.mine(actor.id()));
    }

    @PostMapping(value = "/materials/pairs/{pairId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<PairDetailResponse> upload(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long pairId,
                                                  @RequestPart MultipartFile file,
                                                  @RequestParam(required = false) String description,
                                                  HttpServletRequest request) {
        return ApiResponse.success(service.uploadPhoto(actor, pairId, file, description, request));
    }

    @PostMapping(value = "/materials/photos/{photoId}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<PairDetailResponse> resubmit(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long photoId,
                                                    @RequestPart MultipartFile file,
                                                    @RequestParam(required = false) String description,
                                                    HttpServletRequest request) {
        return ApiResponse.success(service.resubmitPhoto(actor, photoId, file, description, request));
    }

    @PostMapping("/materials/photos/{photoId}/peer-confirm")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<PairDetailResponse> confirm(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long photoId,
                                                   HttpServletRequest request) {
        return ApiResponse.success(service.confirmPhoto(actor, photoId, request));
    }

    @PostMapping("/materials/pairs/{pairId}/reflections")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<PairDetailResponse> reflection(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long pairId,
                                                      @Valid @RequestBody ReflectionSubmitRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.submitReflection(actor, pairId, body, request));
    }

    @GetMapping("/admin/materials/pairs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<PairDetailResponse>> list(@RequestParam(required = false) Long periodId) {
        return ApiResponse.success(service.adminList(periodId));
    }

    @PostMapping("/admin/materials/photos/{photoId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PairDetailResponse> reviewPhoto(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long photoId,
                                                       @Valid @RequestBody MaterialReviewRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.reviewPhoto(actor, photoId, body, request));
    }

    @PostMapping("/admin/materials/pairs/{pairId}/accept")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PairDetailResponse> accept(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long pairId,
                                                  @Valid @RequestBody PairActionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.accept(actor, pairId, body, request));
    }

    @PostMapping("/admin/materials/pairs/{pairId}/end")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PairDetailResponse> end(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long pairId,
                                               @Valid @RequestBody PairActionRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.end(actor, pairId, body, request));
    }

    @GetMapping("/materials/photos/{photoId}/file/access")
    public ResponseEntity<ApiResponse<String>> access(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long photoId) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(ApiResponse.success(service.accessUrl(actor,photoId)));
    }

    @GetMapping("/materials/photos/{photoId}/file")
    public ResponseEntity<Resource> file(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long photoId) {
        var download = service.download(actor, photoId);
        ContentDisposition disposition = ContentDisposition.inline().filename(download.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.mimeType())).contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(download.resource());
    }
}
