package kz.damulab.lectures;

public record LectureAttachmentResponse(
        Long id,
        String title,
        String url,
        String mediaType,
        String storageKey,
        String originalFileName,
        String fileContentType,
        Long fileSizeBytes,
        boolean uploaded,
        int sortOrder
) {
}
