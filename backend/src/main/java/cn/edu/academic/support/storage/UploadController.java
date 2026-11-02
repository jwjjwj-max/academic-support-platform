package cn.edu.academic.support.storage;

import cn.edu.academic.support.common.api.ApiResponse;
import cn.edu.academic.support.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor
@RequestMapping("/storage/uploads")
public class UploadController {
    private final UploadService service;
    @GetMapping("/capabilities") public ApiResponse<Map<String,Boolean>> capabilities(){return ApiResponse.success(Map.of("directUpload",service.enabled()));}
    @PostMapping public ResponseEntity<?> begin(@AuthenticationPrincipal AuthenticatedUser actor,@RequestBody UploadRequest body)throws Exception {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.begin(actor,body)));
    }
    @PostMapping("/{id}/confirm") public ResponseEntity<?> confirm(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,HttpServletRequest http)throws Exception {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.finish(actor,id,http)));
    }
}
