package kz.damulab.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.content.AtomicSkill;
import kz.damulab.content.Topic;
import kz.damulab.parentlink.ParentStudentLinkRepository;
import kz.damulab.questions.QuestionVersion;
import kz.damulab.testing.AnswerEvaluation;
import kz.damulab.testing.AnswerEvaluationRepository;
import kz.damulab.testing.StudentAnswerRepository;
import kz.damulab.testing.TestResult;
import kz.damulab.testing.TestResultRepository;
import kz.damulab.testing.TestSessionQuestion;
import kz.damulab.users.ParentProfile;
import kz.damulab.users.ParentProfileRepository;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

@Service
public class AnalyticsService {

    private final SkillMasteryRepository masteryRepository;
    private final TestResultRepository results;
    private final AnswerEvaluationRepository evaluations;
    private final StudentAnswerRepository answers;
    private final StudentProfileRepository students;
    private final ParentProfileRepository parents;
    private final ParentStudentLinkRepository links;
    private final ObjectMapper objectMapper;

    public AnalyticsService(
            SkillMasteryRepository masteryRepository,
            TestResultRepository results,
            AnswerEvaluationRepository evaluations,
            StudentAnswerRepository answers,
            StudentProfileRepository students,
            ParentProfileRepository parents,
            ParentStudentLinkRepository links,
            ObjectMapper objectMapper
    ) {
        this.masteryRepository = masteryRepository;
        this.results = results;
        this.evaluations = evaluations;
        this.answers = answers;
        this.students = students;
        this.parents = parents;
        this.links = links;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void updateMastery(TestResult result) {
        List<AnswerEvaluation> sessionEvaluations = evaluations.findBySessionQuestionSessionId(result.getSession().getId());
        Map<String, AttemptAggregate> aggregates = new LinkedHashMap<>();
        for (AnswerEvaluation evaluation : sessionEvaluations) {
            TestSessionQuestion sessionQuestion = evaluation.getSessionQuestion();
            QuestionVersion version = sessionQuestion.getQuestionVersion();
            aggregate(aggregates, "topic:" + version.getTopic().getId(), version.getTopic(), null, evaluation, sessionQuestion);
            if (version.getAtomicSkill() != null) {
                aggregate(
                        aggregates,
                        "skill:" + version.getAtomicSkill().getId(),
                        version.getTopic(),
                        version.getAtomicSkill(),
                        evaluation,
                        sessionQuestion
                );
            }
        }
        OffsetDateTime attemptedAt = result.getCreatedAt();
        StudentProfile student = result.getSession().getStudentProfile();
        aggregates.values().forEach(aggregate -> applyAggregate(student, aggregate, attemptedAt));
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse currentStudentSummary(Principal principal) {
        StudentProfile student = students.findByUserEmailIgnoreCase(principal.getName())
                .orElseThrow(() -> new AnalyticsException("student_analytics_not_found"));
        return summary(student);
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse accessibleStudentSummary(Principal principal, Long studentId) {
        return summary(accessibleStudent(principal.getName(), studentId));
    }

    @Transactional(readOnly = true)
    public List<TimelineItemResponse> timeline(Principal principal, Long studentId) {
        return timeline(accessibleStudent(principal.getName(), studentId));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeMapItemResponse> knowledgeMap(Principal principal, Long studentId) {
        return knowledgeMap(accessibleStudent(principal.getName(), studentId));
    }

    @Transactional(readOnly = true)
    public List<LastErrorResponse> lastErrors(Principal principal, Long studentId) {
        return lastErrors(accessibleStudent(principal.getName(), studentId));
    }

    @Transactional(readOnly = true)
    public TrajectoryResponse trajectory(Principal principal, Long studentId) {
        return trajectory(accessibleStudent(principal.getName(), studentId));
    }

    @Transactional(readOnly = true)
    public PredictionResponse prediction(Principal principal, Long studentId) {
        return prediction(accessibleStudent(principal.getName(), studentId));
    }

    private AnalyticsSummaryResponse summary(StudentProfile student) {
        List<TimelineItemResponse> timeline = timeline(student);
        List<KnowledgeMapItemResponse> knowledgeMap = knowledgeMap(student);
        int averagePercent = averagePercent(timeline);
        int overallMastery = overallMastery(knowledgeMap);
        return new AnalyticsSummaryResponse(
                student.getId(),
                student.getUser().getFullName(),
                timeline.size(),
                averagePercent,
                overallMastery,
                timeline.isEmpty() ? null : timeline.get(0).finishedAt(),
                knowledgeMap,
                lastErrors(student),
                timeline,
                trajectory(student, knowledgeMap, overallMastery),
                prediction(timeline)
        );
    }

    private List<TimelineItemResponse> timeline(StudentProfile student) {
        String language = student.getPreferredLanguage();
        return results.findTop10BySessionStudentProfileIdOrderByCreatedAtDesc(student.getId()).stream()
                .map(result -> new TimelineItemResponse(
                        result.getId(),
                        result.getSession().getId(),
                        result.getSession().getTestType().name(),
                        result.getSession().getSubject() == null
                                ? null
                                : localized(result.getSession().getSubject().getTitleRu(), result.getSession().getSubject().getTitleKk(), language),
                        result.getSession().getGrade() == null
                                ? null
                                : localized(result.getSession().getGrade().getTitleRu(), result.getSession().getGrade().getTitleKk(), language),
                        result.getPercent(),
                        result.getScore(),
                        result.getMaxScore(),
                        result.getTotalQuestions(),
                        result.getCorrectAnswers(),
                        result.getSession().getFinishedAt() == null ? result.getCreatedAt() : result.getSession().getFinishedAt()
                ))
                .toList();
    }

    private List<KnowledgeMapItemResponse> knowledgeMap(StudentProfile student) {
        String language = student.getPreferredLanguage();
        return masteryRepository.findByStudentProfileIdOrderByMasteryPercentAscUpdatedAtDesc(student.getId()).stream()
                .map(mastery -> {
                    Topic topic = mastery.getTopic();
                    AtomicSkill skill = mastery.getAtomicSkill();
                    int percent = mastery.getMasteryPercent().setScale(0, RoundingMode.HALF_UP).intValue();
                    return new KnowledgeMapItemResponse(
                            topic.getId(),
                            localized(topic.getTitleRu(), topic.getTitleKk(), language),
                            skill == null ? null : skill.getId(),
                            skill == null ? null : localized(skill.getTitleRu(), skill.getTitleKk(), language),
                            percent,
                            mastery.getAttempts(),
                            mastery.getCorrectAnswers(),
                            mastery.getTotalQuestions(),
                            status(percent),
                            mastery.getUpdatedAt()
                    );
                })
                .toList();
    }

    private List<LastErrorResponse> lastErrors(StudentProfile student) {
        String language = student.getPreferredLanguage();
        return evaluations.findTop5BySessionQuestionSessionStudentProfileIdAndCorrectFalseOrderByEvaluatedAtDesc(student.getId()).stream()
                .map(evaluation -> {
                    TestSessionQuestion sessionQuestion = evaluation.getSessionQuestion();
                    QuestionVersion version = sessionQuestion.getQuestionVersion();
                    return new LastErrorResponse(
                            sessionQuestion.getSession().getId(),
                            sessionQuestion.getId(),
                            localized(version.getBodyRu(), version.getBodyKk(), language),
                            localized(version.getTopic().getTitleRu(), version.getTopic().getTitleKk(), language),
                            version.getAtomicSkill() == null
                                    ? null
                                    : localized(version.getAtomicSkill().getTitleRu(), version.getAtomicSkill().getTitleKk(), language),
                            version.getDifficulty(),
                            evaluation.getPointsAwarded(),
                            sessionQuestion.getPoints(),
                            answers.findBySessionQuestionId(sessionQuestion.getId())
                                    .map(answer -> parseMap(answer.getAnswerJson()))
                                    .orElse(Map.of()),
                            localized(version.getExplanationRu(), version.getExplanationKk(), language),
                            evaluation.getEvaluatedAt()
                    );
                })
                .toList();
    }

    private TrajectoryResponse trajectory(StudentProfile student) {
        List<KnowledgeMapItemResponse> map = knowledgeMap(student);
        return trajectory(student, map, overallMastery(map));
    }

    private TrajectoryResponse trajectory(
            StudentProfile student,
            List<KnowledgeMapItemResponse> knowledgeMap,
            int overallMastery
    ) {
        List<String> weakTopics = knowledgeMap.stream()
                .filter(item -> item.atomicSkillId() == null)
                .filter(item -> item.masteryPercent() < 75)
                .limit(3)
                .map(KnowledgeMapItemResponse::topicTitle)
                .toList();
        String nextFocus = weakTopics.isEmpty() ? null : weakTopics.get(0);
        String message = nextFocus == null
                ? "Базовая траектория стабильна: продолжайте регулярные предметные срезы."
                : "Следующий фокус: " + nextFocus + ". Повторите тему и пройдите короткий предметный срез.";
        return new TrajectoryResponse(overallMastery, nextFocus, weakTopics, message);
    }

    private PredictionResponse prediction(StudentProfile student) {
        return prediction(timeline(student));
    }

    private PredictionResponse prediction(List<TimelineItemResponse> timeline) {
        int expected = averagePercent(timeline);
        int count = timeline.size();
        String confidence = count >= 5 ? "medium" : count >= 2 ? "low" : "insufficient";
        String message = count == 0
                ? "Недостаточно результатов для прогноза."
                : "Ожидаемый процент по ближайшему срезу на базе последних результатов: " + expected + "%.";
        return new PredictionResponse(expected, confidence, count, message);
    }

    private StudentProfile accessibleStudent(String email, Long studentId) {
        StudentProfile student = students.findById(studentId)
                .orElseThrow(() -> new AnalyticsException("student_analytics_not_found"));
        if (student.getUser().getEmail().equalsIgnoreCase(email)) {
            return student;
        }
        ParentProfile parent = parents.findByUserEmailIgnoreCase(email).orElse(null);
        if (parent != null && links.existsByParentProfileAndStudentProfile(parent, student)) {
            return student;
        }
        throw new AnalyticsException("student_analytics_not_found");
    }

    private void aggregate(
            Map<String, AttemptAggregate> aggregates,
            String key,
            Topic topic,
            AtomicSkill skill,
            AnswerEvaluation evaluation,
            TestSessionQuestion sessionQuestion
    ) {
        AttemptAggregate aggregate = aggregates.computeIfAbsent(key, ignored -> new AttemptAggregate(topic, skill));
        aggregate.total++;
        aggregate.correct += evaluation.isCorrect() ? 1 : 0;
        aggregate.score = aggregate.score.add(evaluation.getPointsAwarded());
        aggregate.maxScore = aggregate.maxScore.add(sessionQuestion.getPoints());
        aggregate.difficultyTotal += sessionQuestion.getQuestionVersion().getDifficulty();
    }

    private void applyAggregate(StudentProfile student, AttemptAggregate aggregate, OffsetDateTime attemptedAt) {
        SkillMastery mastery = aggregate.skill == null
                ? masteryRepository.findFirstByStudentProfileIdAndTopicIdAndAtomicSkillIsNull(student.getId(), aggregate.topic.getId())
                        .orElseGet(() -> new SkillMastery(student, aggregate.topic, null))
                : masteryRepository.findFirstByStudentProfileIdAndTopicIdAndAtomicSkillId(
                        student.getId(),
                        aggregate.topic.getId(),
                        aggregate.skill.getId()
                ).orElseGet(() -> new SkillMastery(student, aggregate.topic, aggregate.skill));
        mastery.applyAttempt(aggregate.correct, aggregate.total, aggregate.percent(), aggregate.averageDifficulty(), attemptedAt);
        masteryRepository.save(mastery);
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private int averagePercent(List<TimelineItemResponse> timeline) {
        if (timeline.isEmpty()) {
            return 0;
        }
        return (int) Math.round(timeline.stream().mapToInt(TimelineItemResponse::percent).average().orElse(0));
    }

    private int overallMastery(List<KnowledgeMapItemResponse> knowledgeMap) {
        List<KnowledgeMapItemResponse> topicRows = knowledgeMap.stream()
                .filter(item -> item.atomicSkillId() == null)
                .toList();
        if (topicRows.isEmpty()) {
            return 0;
        }
        return (int) Math.round(topicRows.stream().mapToInt(KnowledgeMapItemResponse::masteryPercent).average().orElse(0));
    }

    private String status(int percent) {
        if (percent < 50) {
            return "weak";
        }
        if (percent < 75) {
            return "watch";
        }
        return "strong";
    }

    private String localized(String ru, String kk, String language) {
        return "kk".equals(language) && kk != null && !kk.isBlank() ? kk : ru;
    }

    private static final class AttemptAggregate {
        private final Topic topic;
        private final AtomicSkill skill;
        private int correct;
        private int total;
        private int difficultyTotal;
        private BigDecimal score = BigDecimal.ZERO;
        private BigDecimal maxScore = BigDecimal.ZERO;

        private AttemptAggregate(Topic topic, AtomicSkill skill) {
            this.topic = topic;
            this.skill = skill;
        }

        private BigDecimal percent() {
            if (maxScore.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 2, RoundingMode.HALF_UP);
        }

        private int averageDifficulty() {
            if (total == 0) {
                return 1;
            }
            return Math.max(1, Math.round((float) difficultyTotal / total));
        }
    }
}
