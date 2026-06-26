package kz.damulab.testing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.analytics.AnalyticsService;
import kz.damulab.config.DamulabTestingProperties;
import kz.damulab.content.Grade;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.Subject;
import kz.damulab.content.SubjectRepository;
import kz.damulab.gamification.AchievementUnlockPayload;
import kz.damulab.gamification.StudentEngagementService;
import kz.damulab.questions.QuestionVersion;
import kz.damulab.questions.QuestionVersionRepository;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

@Service
public class TestingHubService {

    private static final int FALLBACK_TIME_LIMIT_SECONDS = 1500;

    private final TestTemplateRepository templates;
    private final TestSessionRepository sessions;
    private final TestSessionQuestionRepository sessionQuestions;
    private final StudentAnswerRepository answers;
    private final AnswerEvaluationRepository evaluations;
    private final TestResultRepository results;
    private final QuestionVersionRepository questionVersions;
    private final SubjectRepository subjects;
    private final GradeRepository grades;
    private final StudentProfileRepository students;
    private final AnswerChecker answerChecker;
    private final AnalyticsService analyticsService;
    private final StudentEngagementService engagementService;
    private final ObjectMapper objectMapper;
    private final DamulabTestingProperties testingProperties;
    private final TestStartAvailabilityService testStartAvailability;

    public TestingHubService(
            TestTemplateRepository templates,
            TestSessionRepository sessions,
            TestSessionQuestionRepository sessionQuestions,
            StudentAnswerRepository answers,
            AnswerEvaluationRepository evaluations,
            TestResultRepository results,
            QuestionVersionRepository questionVersions,
            SubjectRepository subjects,
            GradeRepository grades,
            StudentProfileRepository students,
            AnswerChecker answerChecker,
            AnalyticsService analyticsService,
            StudentEngagementService engagementService,
            ObjectMapper objectMapper,
            DamulabTestingProperties testingProperties,
            TestStartAvailabilityService testStartAvailability
    ) {
        this.templates = templates;
        this.sessions = sessions;
        this.sessionQuestions = sessionQuestions;
        this.answers = answers;
        this.evaluations = evaluations;
        this.results = results;
        this.questionVersions = questionVersions;
        this.subjects = subjects;
        this.grades = grades;
        this.students = students;
        this.answerChecker = answerChecker;
        this.analyticsService = analyticsService;
        this.engagementService = engagementService;
        this.objectMapper = objectMapper;
        this.testingProperties = testingProperties;
        this.testStartAvailability = testStartAvailability;
    }

    @Transactional
    public TestSessionResponse startSession(String studentEmail, StartTestSessionRequest request) {
        StudentProfile student = findStudent(studentEmail);
        Subject subject = subjects.findById(request.getSubjectId())
                .orElseThrow(() -> new TestingHubException("subject_not_found"));
        Grade grade = grades.findById(request.getGradeId())
                .orElseThrow(() -> new TestingHubException("grade_not_found"));
        if (!testStartAvailability.isPairAvailable(subject.getId(), grade.getId())) {
            throw new TestingHubException("published_questions_not_found");
        }
        TestType testType = request.getTestType() == null ? TestType.SUBJECT : request.getTestType();
        TestTemplate template = templates.findFirstByTestTypeAndActiveTrue(testType).orElse(null);
        int timeLimit = template == null ? FALLBACK_TIME_LIMIT_SECONDS : template.getTimeLimitSeconds();
        String language = normalizeLanguage(request.getLanguage(), student.getPreferredLanguage());

        int fetchPool = Math.min(500, Math.max(testingProperties.getMaxQuestionCount() * 25, 50));
        List<QuestionVersion> pool = new ArrayList<>(questionVersions.findPublishedForTest(
                subject.getId(),
                grade.getId(),
                request.getDifficulty(),
                PageRequest.of(0, fetchPool)
        ));
        if (pool.isEmpty()) {
            throw new TestingHubException("published_questions_not_found");
        }
        Collections.shuffle(pool, ThreadLocalRandom.current());
        int requested = testingProperties.getDefaultQuestionCount();
        if (request.getQuestionCount() != null) {
            requested = Math.min(request.getQuestionCount(), testingProperties.getMaxQuestionCount());
        }
        requested = Math.min(requested, testingProperties.getMaxQuestionCount());
        int targetCount = Math.min(requested, pool.size());
        List<QuestionVersion> selected = pool.subList(0, targetCount);

        TestSession session = sessions.save(new TestSession(
                student,
                testType,
                subject,
                grade,
                language,
                request.getDifficulty(),
                timeLimit,
                settingsJson(targetCount)
        ));
        int orderNo = 1;
        for (QuestionVersion version : selected) {
            sessionQuestions.save(new TestSessionQuestion(session, version, orderNo, BigDecimal.ONE));
            orderNo++;
        }
        return getSession(studentEmail, session.getId());
    }

    @Transactional(readOnly = true)
    public TestSessionResponse getSession(String studentEmail, Long sessionId) {
        StudentProfile student = findStudent(studentEmail);
        TestSession session = findOwnedSession(sessionId, student);
        Map<Long, StudentAnswer> answerByQuestion = answerMap(sessionId);
        return toSessionResponse(session, answerByQuestion);
    }

    @Transactional
    public TestSessionResponse submitAnswer(String studentEmail, Long sessionId, SubmitAnswerRequest request) {
        StudentProfile student = findStudent(studentEmail);
        TestSession session = findOwnedSession(sessionId, student);
        if (session.getStatus() == TestSessionStatus.FINISHED) {
            throw new TestingHubException("session_finished");
        }
        TestSessionQuestion question = sessionQuestions.findByIdAndSessionId(request.sessionQuestionId(), session.getId())
                .orElseThrow(() -> new TestingHubException("session_question_not_found"));
        saveAnswer(question, request.answer());
        return getSession(studentEmail, sessionId);
    }

    @Transactional
    public TestResultResponse finishSession(String studentEmail, Long sessionId) {
        StudentProfile student = findStudent(studentEmail);
        TestSession session = findOwnedSession(sessionId, student);
        Optional<TestResult> existing = results.findBySessionId(session.getId());
        if (existing.isPresent()) {
            return resultResponse(existing.get(), session);
        }

        List<TestSessionQuestion> questions = sessionQuestions.findBySessionIdOrderByOrderNoAsc(session.getId());
        if (questions.isEmpty()) {
            throw new TestingHubException("session_questions_not_found");
        }

        int correctAnswers = 0;
        BigDecimal score = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
        for (TestSessionQuestion question : questions) {
            maxScore = maxScore.add(question.getPoints());
            StudentAnswer answer = answers.findBySessionQuestionId(question.getId())
                    .orElse(new StudentAnswer(question, "{}"));
            AnswerCheckResult check = answerChecker.check(question.getQuestionVersion(), toJsonNode(answer.getAnswerJson()), question.getPoints());
            if (check.correct()) {
                correctAnswers++;
            }
            score = score.add(check.pointsAwarded());
            evaluations.save(new AnswerEvaluation(question, check.correct(), check.pointsAwarded(), check.detailsJson()));
        }
        int percent = maxScore.compareTo(BigDecimal.ZERO) == 0
                ? 0
                : score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 0, RoundingMode.HALF_UP).intValue();
        session.finish();
        TestResult result = results.save(new TestResult(session, questions.size(), correctAnswers, score, maxScore, percent));
        analyticsService.updateMastery(result);
        List<AchievementUnlockPayload> unlocked = engagementService.recordUsefulActivity(result);
        return resultResponse(result, session, unlocked);
    }

    @Transactional(readOnly = true)
    public TestResultResponse getResult(String studentEmail, Long sessionId) {
        StudentProfile student = findStudent(studentEmail);
        TestSession session = findOwnedSession(sessionId, student);
        TestResult result = results.findBySessionId(session.getId())
                .orElseThrow(() -> new TestingHubException("result_not_found"));
        return resultResponse(result, session);
    }

    @Transactional(readOnly = true)
    public List<TestSessionResponse> recentSessions(String studentEmail) {
        StudentProfile student = findStudent(studentEmail);
        return sessions.findTop10ByStudentProfileIdOrderByStartedAtDesc(student.getId()).stream()
                .map(session -> toSessionResponse(session, Map.of()))
                .toList();
    }

    public JsonNode choiceAnswer(List<String> selected) {
        return objectNode(Map.of("selected", selected));
    }

    public JsonNode matchingAnswer(Map<String, String> pairs) {
        return objectNode(Map.of("pairs", pairs));
    }

    public JsonNode fillAnswer(Map<String, String> fillAnswers) {
        return objectNode(Map.of("answers", fillAnswers));
    }

    void saveAnswer(TestSessionQuestion question, JsonNode answerJson) {
        String json = toJson(answerJson);
        StudentAnswer existing = answers.findBySessionQuestionId(question.getId()).orElse(null);
        if (existing == null) {
            answers.save(new StudentAnswer(question, json));
        } else {
            existing.replaceAnswer(json);
        }
    }

    private TestResultResponse resultResponse(TestResult result, TestSession session) {
        return resultResponse(result, session, List.of());
    }

    private TestResultResponse resultResponse(
            TestResult result,
            TestSession session,
            List<AchievementUnlockPayload> newlyUnlocked
    ) {
        Map<Long, StudentAnswer> answerByQuestion = answerMap(session.getId());
        Map<Long, AnswerEvaluation> evaluationByQuestion = evaluationMap(session.getId());
        List<QuestionResultResponse> questionResults = sessionQuestions.findBySessionIdOrderByOrderNoAsc(session.getId()).stream()
                .map(question -> toQuestionResult(question, answerByQuestion.get(question.getId()), evaluationByQuestion.get(question.getId())))
                .toList();
        return new TestResultResponse(
                result.getId(),
                session.getId(),
                result.getTotalQuestions(),
                result.getCorrectAnswers(),
                result.getScore(),
                result.getMaxScore(),
                result.getPercent(),
                result.getCreatedAt(),
                questionResults,
                List.copyOf(newlyUnlocked)
        );
    }

    private QuestionResultResponse toQuestionResult(
            TestSessionQuestion question,
            StudentAnswer answer,
            AnswerEvaluation evaluation
    ) {
        QuestionVersion version = question.getQuestionVersion();
        String language = question.getSession().getLanguage();
        return new QuestionResultResponse(
                question.getId(),
                question.getOrderNo(),
                version.getType().name(),
                body(version, language),
                topicTitle(version, language),
                skillTitle(version, language),
                evaluation != null && evaluation.isCorrect(),
                question.getPoints(),
                evaluation == null ? BigDecimal.ZERO : evaluation.getPointsAwarded(),
                answer == null ? Map.of() : answerChecker.submittedAnswer(answer.getAnswerJson()),
                answerChecker.publicCorrectAnswer(version),
                answerChecker.choices(version, language),
                matchingPairs(version, language),
                answerChecker.matchingResultRows(
                        version,
                        answer == null ? "{}" : answer.getAnswerJson(),
                        language
                ),
                explanation(version, language)
        );
    }

    private TestSessionResponse toSessionResponse(TestSession session, Map<Long, StudentAnswer> answerByQuestion) {
        List<SessionQuestionResponse> questions = sessionQuestions.findBySessionIdOrderByOrderNoAsc(session.getId()).stream()
                .map(question -> toQuestionResponse(question, answerByQuestion.containsKey(question.getId())))
                .toList();
        return new TestSessionResponse(
                session.getId(),
                session.getStatus().name().toLowerCase(),
                session.getTestType().name(),
                session.getSubject() == null ? null : session.getSubject().getId(),
                session.getSubject() == null ? null : localized(session.getSubject().getTitleRu(), session.getSubject().getTitleKk(), session.getLanguage()),
                session.getGrade() == null ? null : session.getGrade().getId(),
                session.getGrade() == null ? null : localized(session.getGrade().getTitleRu(), session.getGrade().getTitleKk(), session.getLanguage()),
                session.getLanguage(),
                session.getDifficulty(),
                session.getStartedAt(),
                session.getFinishedAt(),
                session.getTimeLimitSeconds(),
                questions
        );
    }

    private SessionQuestionResponse toQuestionResponse(TestSessionQuestion question, boolean answered) {
        QuestionVersion version = question.getQuestionVersion();
        String language = question.getSession().getLanguage();
        var primaryTopic = version.getPrimaryTopic();
        return new SessionQuestionResponse(
                question.getId(),
                question.getOrderNo(),
                version.getType().name(),
                body(version, language),
                primaryTopic == null ? null : primaryTopic.getId(),
                topicTitle(version, language),
                version.getAtomicSkill() == null ? null : version.getAtomicSkill().getId(),
                skillTitle(version, language),
                version.getDifficulty(),
                question.getPoints(),
                shuffledChoices(version, language),
                shuffledMatching(version, language, true),
                shuffledMatching(version, language, false),
                answerChecker.fillPlaceholders(version),
                answered
        );
    }

    private List<ChoiceDisplay> shuffledChoices(QuestionVersion version, String language) {
        List<ChoiceDisplay> list = new ArrayList<>(answerChecker.choices(version, language));
        Collections.shuffle(list, ThreadLocalRandom.current());
        return list;
    }

    private List<MatchingDisplay> shuffledMatching(QuestionVersion version, String language, boolean left) {
        List<MatchingDisplay> list = new ArrayList<>(
                left ? answerChecker.matchingLeft(version, language) : answerChecker.matchingRight(version, language)
        );
        Collections.shuffle(list, ThreadLocalRandom.current());
        return list;
    }

    private Map<Long, StudentAnswer> answerMap(Long sessionId) {
        Map<Long, StudentAnswer> map = new LinkedHashMap<>();
        answers.findBySessionQuestionSessionId(sessionId)
                .forEach(answer -> map.put(answer.getSessionQuestion().getId(), answer));
        return map;
    }

    private Map<Long, AnswerEvaluation> evaluationMap(Long sessionId) {
        Map<Long, AnswerEvaluation> map = new LinkedHashMap<>();
        evaluations.findBySessionQuestionSessionId(sessionId)
                .forEach(evaluation -> map.put(evaluation.getSessionQuestion().getId(), evaluation));
        return map;
    }

    private List<Map<String, String>> matchingPairs(QuestionVersion version, String language) {
        if (version.getType() != kz.damulab.questions.QuestionType.MATCHING) {
            return List.of();
        }
        List<MatchingDisplay> left = answerChecker.matchingLeft(version, language);
        List<MatchingDisplay> right = answerChecker.matchingLeft(version, language).stream()
                .map(item -> new MatchingDisplay(item.value(), correctRight(version, item.value(), language)))
                .toList();
        List<Map<String, String>> result = new java.util.ArrayList<>();
        for (int i = 0; i < left.size(); i++) {
            result.add(Map.of("left", left.get(i).text(), "right", right.get(i).text()));
        }
        return result;
    }

    private String correctRight(QuestionVersion version, String leftValue, String language) {
        JsonNode pairs = toJsonNode(version.getOptionsJson());
        for (JsonNode pair : pairs) {
            if (leftValue.equals(pair.path("leftRu").asText())) {
                return "kk".equals(language) ? pair.path("rightKk").asText() : pair.path("rightRu").asText();
            }
        }
        return "";
    }

    private String body(QuestionVersion version, String language) {
        return localized(version.getBodyRu(), version.getBodyKk(), language);
    }

    private String explanation(QuestionVersion version, String language) {
        return studentFacingExplanation(version, language);
    }

    /** Сначала мини-лекция, иначе короткое legacy-поле explanation. */
    private String studentFacingExplanation(QuestionVersion version, String language) {
        String mini = localized(version.getMiniLectureRu(), version.getMiniLectureKk(), language);
        if (mini != null && !mini.isBlank()) {
            return mini;
        }
        return localized(version.getExplanationRu(), version.getExplanationKk(), language);
    }

    private String topicTitle(QuestionVersion version, String language) {
        var topic = version.getPrimaryTopic();
        if (topic == null) {
            return "";
        }
        return localized(topic.getTitleRu(), topic.getTitleKk(), language);
    }

    private String skillTitle(QuestionVersion version, String language) {
        if (version.getAtomicSkill() == null) {
            return null;
        }
        return localized(version.getAtomicSkill().getTitleRu(), version.getAtomicSkill().getTitleKk(), language);
    }

    private String localized(String ru, String kk, String language) {
        return "kk".equals(language) && kk != null && !kk.isBlank() ? kk : ru;
    }

    private StudentProfile findStudent(String email) {
        return students.findByUserEmailIgnoreCase(email)
                .orElseThrow(() -> new TestingHubException("student_profile_not_found"));
    }

    private TestSession findOwnedSession(Long sessionId, StudentProfile student) {
        return sessions.findByIdAndStudentProfileId(sessionId, student.getId())
                .orElseThrow(() -> new TestingHubException("test_session_not_found"));
    }

    private String normalizeLanguage(String requested, String fallback) {
        String value = requested == null || requested.isBlank() ? fallback : requested;
        return "kk".equals(value) ? "kk" : "ru";
    }

    private String settingsJson(int questionCount) {
        return toJson(Map.of("questionCount", questionCount));
    }

    private JsonNode objectNode(Object value) {
        return objectMapper.valueToTree(value);
    }

    private JsonNode toJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new TestingHubException("answer_payload_invalid");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new TestingHubException("answer_payload_invalid");
        }
    }
}
