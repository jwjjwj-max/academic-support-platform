package cn.edu.academic.support.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StudentProfileUpdateRequest(
        @NotBlank(message = "请输入学号") @Pattern(regexp = "^[A-Za-z0-9_-]{4,32}$", message = "学号格式不正确") String studentNo,
        @NotBlank(message = "请输入姓名") @Size(max = 64, message = "姓名不能超过 64 个字符") String realName,
        @NotBlank(message = "请输入学院") @Pattern(regexp = "信息技术与人工智能学院", message = "学院须为信息技术与人工智能学院") String college,
        @NotBlank(message = "请输入专业") @Pattern(regexp = "人工智能|软件工程|计算机科学与技术", message = "请选择人工智能、软件工程或计算机科学与技术") String major,
        @Min(value = 2000, message = "年级格式不正确") @Max(value = 2100, message = "年级格式不正确") Integer grade,
        @NotBlank(message = "请输入微信号") @Size(max = 100, message = "微信号不能超过 100 个字符") String wechat,
        @Pattern(regexp = "^$|^[0-9+ -]{6,32}$", message = "联系电话格式不正确") String phone
) {
}
