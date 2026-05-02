package kz.damulab.content;

public record AtomicSkillResponse(
        Long id,
        Long topicId,
        String code,
        String titleRu,
        String titleKk,
        boolean active
) {
}
