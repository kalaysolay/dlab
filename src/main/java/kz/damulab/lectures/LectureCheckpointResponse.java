package kz.damulab.lectures;

public record LectureCheckpointResponse(
        Long id,
        Long questionVersionId,
        String type,
        String bodyRu,
        String bodyKk,
        int sortOrder
) {
}
