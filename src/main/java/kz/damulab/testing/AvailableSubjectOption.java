package kz.damulab.testing;

import java.util.List;

public record AvailableSubjectOption(long id, String titleRu, List<AvailableGradeOption> grades) {
}
