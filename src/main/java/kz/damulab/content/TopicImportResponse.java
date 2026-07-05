package kz.damulab.content;

public record TopicImportResponse(
        int totalTopics,
        int createdTopics,
        int updatedTopics,
        Long subjectId,
        Long gradeId,
        String importNote
) {
}
