package cn.edu.academic.support.user.dto;

import cn.edu.academic.support.user.model.StudentProfile;

import java.time.LocalDateTime;

public record StudentProfileResponse(
        Long userId,
        String studentNo,
        String realName,
        String college,
        String major,
        Integer grade,
        String wechat,
        String phone,
        String reviewStatus,
        String rejectionReason,
        Integer profileVersion,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {
    public static StudentProfileResponse from(StudentProfile profile) {
        return new StudentProfileResponse(profile.getUserId(), profile.getStudentNo(), profile.getRealName(),
                profile.getCollege(), profile.getMajor(), profile.getGrade(), profile.getWechat(), profile.getPhone(),
                profile.getReviewStatus(), profile.getRejectionReason(), profile.getProfileVersion(),
                profile.getSubmittedAt(), profile.getReviewedAt());
    }
}

