package cn.edu.academic.support.notification.dto;
import jakarta.validation.constraints.*;
public record NotificationCreateRequest(
        Long periodId,
        @NotBlank @Size(max=200) String title,
        @NotBlank @Size(max=2000) String content,
        @NotBlank @Pattern(regexp="ALL_STUDENTS|PERIOD_PARTICIPANTS", message="通知范围无效") String audienceType) {}
