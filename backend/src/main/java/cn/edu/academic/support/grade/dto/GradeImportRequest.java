package cn.edu.academic.support.grade.dto;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
public record GradeImportRequest(@NotEmpty(message = "请确认成绩字段映射") Map<String,String> mapping) {}
