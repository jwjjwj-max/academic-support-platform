package cn.edu.academic.support.material.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PairDetailResponse(
        Long id, Long periodId, String periodName, String periodStatus, String pairNo, String status,
        LocalDateTime confirmedAt, LocalDateTime completedAt, String acceptanceNote,
        LocalDateTime endedAt, String endReason, Integer photoMin, Integer photoMax,
        Member navigator, Member climber, List<Photo> photos, List<Reflection> reflections,
        boolean canSubmit, boolean canUploadPhoto, boolean canSubmitReflection, boolean canAccept) {
    public record Member(Long userId, String name, String role) {}
    public record Photo(Long id, Long uploaderId, String uploaderName, String originalFilename,
                        String mimeType, Long fileSize, String description, String status,
                        Integer revisionNo, LocalDateTime submittedAt, LocalDateTime peerConfirmedAt,
                        LocalDateTime reviewedAt, String rejectionReason, boolean canPeerConfirm,
                        boolean canResubmit, String downloadUrl) {}
    public record Reflection(Long id, Long authorId, String authorName, String content, Integer wordCount,
                             String status, LocalDateTime submittedAt, LocalDateTime reviewedAt,
                             String rejectionReason, boolean canResubmit) {}
}
