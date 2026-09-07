package kz.damulab.testing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import kz.damulab.config.DamulabTestingProperties;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.Subject;
import kz.damulab.content.SubjectRepository;
import kz.damulab.questions.QuestionVersionRepository;

/** Формирует согласованный каскад предметов, классов и тем для экрана запуска теста. */
@Service
public class TestStartAvailabilityService {

    private final QuestionVersionRepository questionVersions;
    private final SubjectRepository subjects;
    private final GradeRepository grades;
    private final DamulabTestingProperties testingProperties;

    public TestStartAvailabilityService(
            QuestionVersionRepository questionVersions,
            SubjectRepository subjects,
            GradeRepository grades,
            DamulabTestingProperties testingProperties
    ) {
        this.questionVersions = questionVersions;
        this.subjects = subjects;
        this.grades = grades;
        this.testingProperties = testingProperties;
    }

    /**
     * Показывает только пары предмет/класс, прошедшие минимальный порог банка, и только темы
     * с опубликованными вопросами. Пункт «Все темы» добавляется интерфейсом и имеет значение null.
     */
    public List<AvailableSubjectOption> loadAvailability() {
        long min = testingProperties.getMinPublishedPerSubjectGrade();
        List<Object[]> rows = questionVersions.countPublishedGroupedBySubjectAndGrade(min);
        Map<Long, List<Long>> subjectToGrades = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long subjectId = (Long) row[0];
            Long gradeId = (Long) row[1];
            subjectToGrades.computeIfAbsent(subjectId, key -> new ArrayList<>()).add(gradeId);
        }
        Map<SubjectGradeKey, List<AvailableTopicOption>> topicsBySubjectGrade = new LinkedHashMap<>();
        for (Object[] row : questionVersions.findPublishedTopicsForTestAvailability()) {
            Long topicId = ((Number) row[0]).longValue();
            Long subjectId = ((Number) row[1]).longValue();
            Long gradeId = ((Number) row[2]).longValue();
            String titleRu = (String) row[3];
            topicsBySubjectGrade.computeIfAbsent(new SubjectGradeKey(subjectId, gradeId), ignored -> new ArrayList<>())
                    .add(new AvailableTopicOption(topicId, titleRu));
        }
        List<AvailableSubjectOption> result = new ArrayList<>();
        for (Subject subject : subjects.findAllByOrderByTitleRuAsc()) {
            List<Long> gradeIds = subjectToGrades.get(subject.getId());
            if (gradeIds == null || gradeIds.isEmpty()) {
                continue;
            }
            List<AvailableGradeOption> gradeOptions = new ArrayList<>();
            for (Long gradeId : gradeIds) {
                grades.findById(gradeId).ifPresent(grade ->
                        gradeOptions.add(new AvailableGradeOption(
                                grade.getId(),
                                grade.getTitleRu(),
                                grade.getGradeNo(),
                                List.copyOf(topicsBySubjectGrade.getOrDefault(
                                        new SubjectGradeKey(subject.getId(), grade.getId()),
                                        List.of()
                                ))
                        ))
                );
            }
            gradeOptions.sort(Comparator.comparingInt(AvailableGradeOption::gradeNo));
            result.add(new AvailableSubjectOption(subject.getId(), subject.getTitleRu(), gradeOptions));
        }
        return result;
    }

    public boolean isPairAvailable(Long subjectId, Long gradeId) {
        long min = testingProperties.getMinPublishedPerSubjectGrade();
        long count = questionVersions.countPublishedForSubjectAndGrade(subjectId, gradeId);
        return count >= min;
    }

    private record SubjectGradeKey(Long subjectId, Long gradeId) {
    }
}
