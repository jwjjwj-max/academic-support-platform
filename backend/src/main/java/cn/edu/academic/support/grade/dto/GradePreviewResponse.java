package cn.edu.academic.support.grade.dto;
import java.util.*;
public record GradePreviewResponse(GradeBatchResponse batch,List<String> headers,Map<String,String> suggestedMapping,
 List<Map<String,String>> samples,List<String> validationMessages,int expectedParticipants) {}
