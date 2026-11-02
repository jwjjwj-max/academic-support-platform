package cn.edu.academic.support.period.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PeriodRequest(
        @NotBlank(message = "请输入期次名称") @Size(max = 128) String name,
        @NotBlank(message = "请输入期次简称") @Size(max = 64) String shortName,
        @NotBlank(message = "请选择期次状态")
        @Pattern(regexp = "ANNOUNCED|REGISTRATION|REVIEW|MATCHING|CONFIRMATION|TRACKING|ACCEPTANCE|ENDED|ARCHIVED", message = "期次状态不正确") String status,
        @NotNull(message = "请选择报名开始时间") LocalDateTime registrationStart,
        @NotNull(message = "请选择报名结束时间") LocalDateTime registrationEnd,
        @NotNull(message = "请选择匹配确认截止时间") LocalDateTime confirmationDeadline,
        @NotNull(message = "请选择跟踪开始时间") LocalDateTime trackingStart,
        @NotNull(message = "请选择跟踪结束时间") LocalDateTime trackingEnd,
        @NotNull(message = "请选择验收开始时间") LocalDateTime acceptanceStart,
        @NotNull(message = "请选择验收结束时间") LocalDateTime acceptanceEnd,
        @NotNull @Min(value = 0, message = "照片最少张数不能小于 0") @Max(value = 100, message = "照片最少张数不能超过 100") Integer photoMin,
        @NotNull @Min(value = 0) @Max(value = 100) Integer photoMax,
        @NotNull @Min(value = 0) @Max(value = 100) Integer subjectWeight,
        @NotNull @Min(value = 0) @Max(value = 100) Integer majorWeight,
        @NotNull @Min(value = 0) @Max(value = 100) Integer gradeWeight,
        @NotNull @Min(value = 0) @Max(value = 100) Integer learningStyleWeight,
        @NotNull @Min(value = 0) @Max(value = 100) Integer personalityWeight,
        @NotNull @Min(value = 0) @Max(value = 100) Integer gpaGapWeight
) {
    @AssertTrue(message = "照片最多张数不能小于最少张数")
    public boolean isPhotoRangeValid() {
        return photoMin != null && photoMax != null && photoMax >= photoMin;
    }

    @AssertTrue(message = "六项匹配权重合计必须为 100")
    public boolean isWeightSumValid() {
        return subjectWeight != null && majorWeight != null && gradeWeight != null
                && learningStyleWeight != null && personalityWeight != null && gpaGapWeight != null
                && subjectWeight + majorWeight + gradeWeight + learningStyleWeight + personalityWeight + gpaGapWeight == 100;
    }

    @AssertTrue(message = "期次时间顺序不正确")
    public boolean isTimelineValid() {
        if (registrationStart == null || registrationEnd == null || confirmationDeadline == null
                || trackingStart == null || trackingEnd == null || acceptanceStart == null || acceptanceEnd == null) return false;
        return registrationStart.isBefore(registrationEnd)
                && !registrationEnd.isAfter(confirmationDeadline)
                && !confirmationDeadline.isAfter(trackingStart)
                && trackingStart.isBefore(trackingEnd)
                && !trackingEnd.isAfter(acceptanceStart)
                && acceptanceStart.isBefore(acceptanceEnd);
    }
}

