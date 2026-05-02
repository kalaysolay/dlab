package kz.damulab.content;

import java.util.List;

public record ContentReferencesResponse(
        List<ReferenceOption> subjects,
        List<GradeOption> grades,
        List<TopicResponse> topics
) {
}
