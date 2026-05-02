package kz.damulab.gamification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.analytics.KnowledgeMapItemResponse;
import kz.damulab.analytics.SkillMasteryRepository;
import kz.damulab.testing.TestResult;
import kz.damulab.testing.TestResultRepository;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

@Service
public class StudentEngagementService {

    private static final String COMPLETED_TESTS = "completed_tests";
    private static final String CURRENT_STREAK = "current_streak";

    private final StudentProfileRepository students;
    private final StreakRepository streaks;
    private final AchievementRepository achievements;
    private final StudentAchievementRepository studentAchievements;
    private final TestResultRepository results;
    private final SkillMasteryRepository mastery;
    private final Clock clock;

    public StudentEngagementService(
            StudentProfileRepository students,
            StreakRepository streaks,
            AchievementRepository achievements,
            StudentAchievementRepository studentAchievements,
            TestResultRepository results,
            SkillMasteryRepository mastery,
            Clock clock
    ) {
        this.students = students;
        this.streaks = streaks;
        this.achievements = achievements;
        this.studentAchievements = studentAchievements;
        this.results = results;
        this.mastery = mastery;
        this.clock = clock;
    }

    @Transactional
    public void recordUsefulActivity(TestResult result) {
        StudentProfile student = result.getSession().getStudentProfile();
        OffsetDateTime occurredAt = OffsetDateTime.now(clock);
        recordUsefulActivity(student, occurredAt);
    }

    @Transactional
    public void recordUsefulActivity(StudentProfile student, OffsetDateTime occurredAt) {
        OffsetDateTime timestamp = occurredAt == null ? OffsetDateTime.now(clock) : occurredAt;
        LocalDate activityDate = timestamp.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        Streak streak = streaks.findByStudentProfileId(student.getId())
                .orElseGet(() -> new Streak(student));
        streak.recordActivity(activityDate, timestamp);
        streaks.save(streak);
        awardEligibleAchievements(student, streak, timestamp);
    }

    @Transactional(readOnly = true)
    public StudentDashboardView dashboard(String studentEmail) {
        StudentProfile student = students.findByUserEmailIgnoreCase(studentEmail)
                .orElseThrow(() -> new IllegalStateException("Student profile not found: " + studentEmail));
        String language = student.getPreferredLanguage();
        Streak streak = streaks.findByStudentProfileId(student.getId()).orElse(null);
        List<TestResult> recentResults = results.findTop10BySessionStudentProfileIdOrderByCreatedAtDesc(student.getId());
        long completedTests = results.countBySessionStudentProfileId(student.getId());
        List<KnowledgeMapItemResponse> knowledgeMap = mastery.findByStudentProfileIdOrderByMasteryPercentAscUpdatedAtDesc(student.getId()).stream()
                .map(item -> new KnowledgeMapItemResponse(
                        item.getTopic().getId(),
                        localized(item.getTopic().getTitleRu(), item.getTopic().getTitleKk(), language),
                        item.getAtomicSkill() == null ? null : item.getAtomicSkill().getId(),
                        item.getAtomicSkill() == null ? null : localized(item.getAtomicSkill().getTitleRu(), item.getAtomicSkill().getTitleKk(), language),
                        item.getMasteryPercent().intValue(),
                        item.getAttempts(),
                        item.getCorrectAnswers(),
                        item.getTotalQuestions(),
                        "",
                        item.getUpdatedAt()
                ))
                .toList();
        List<AchievementView> achievementViews = achievementViews(student, language);
        return new StudentDashboardView(
                student.getId(),
                student.getUser().getFullName(),
                language,
                dailyMission(recentResults, knowledgeMap, language),
                streak == null ? new StreakView(0, 0, null) : new StreakView(streak.getCurrentCount(), streak.getLongestCount(), streak.getLastActivityDate()),
                recentResults.isEmpty() ? null : lastActivity(recentResults.get(0), language),
                progress(completedTests, recentResults, knowledgeMap, achievementViews),
                achievementViews
        );
    }

    private void awardEligibleAchievements(StudentProfile student, Streak streak, OffsetDateTime earnedAt) {
        long completedTests = results.countBySessionStudentProfileId(student.getId());
        Map<String, Long> metrics = Map.of(
                COMPLETED_TESTS, completedTests,
                CURRENT_STREAK, (long) streak.getCurrentCount()
        );
        achievements.findByActiveTrueOrderByRequiredValueAscCodeAsc().stream()
                .filter(achievement -> metrics.getOrDefault(achievement.getMetricCode(), 0L) >= achievement.getRequiredValue())
                .filter(achievement -> !studentAchievements.existsByStudentProfileIdAndAchievementId(student.getId(), achievement.getId()))
                .forEach(achievement -> studentAchievements.save(new StudentAchievement(student, achievement, earnedAt)));
    }

    private List<AchievementView> achievementViews(StudentProfile student, String language) {
        Map<Long, StudentAchievement> earnedByAchievementId = studentAchievements
                .findByStudentProfileIdOrderByEarnedAtDesc(student.getId())
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getAchievement().getId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));
        return achievements.findByActiveTrueOrderByRequiredValueAscCodeAsc().stream()
                .map(achievement -> {
                    StudentAchievement earned = earnedByAchievementId.get(achievement.getId());
                    return new AchievementView(
                            achievement.getCode(),
                            achievement.getTitle(language),
                            achievement.getDescription(language),
                            earned != null,
                            earned == null ? null : earned.getEarnedAt(),
                            achievement.getRequiredValue()
                    );
                })
                .toList();
    }

    private DailyMissionView dailyMission(
            List<TestResult> recentResults,
            List<KnowledgeMapItemResponse> knowledgeMap,
            String language
    ) {
        KnowledgeMapItemResponse weakTopic = knowledgeMap.stream()
                .filter(item -> item.atomicSkillId() == null)
                .findFirst()
                .orElse(null);
        if (weakTopic != null && weakTopic.masteryPercent() < 75) {
            String title = "kk".equals(language)
                    ? "Әлсіз тақырыпты бекіту: " + weakTopic.topicTitle()
                    : "Укрепить слабую тему: " + weakTopic.topicTitle();
            String description = "kk".equals(language)
                    ? "Қысқа пәндік тест арқылы прогресті жаңартыңыз."
                    : "Пройдите короткий предметный срез и обновите карту знаний.";
            return new DailyMissionView(title, description, "Перейти к тестам", "/student/tests");
        }
        if (recentResults.isEmpty()) {
            return new DailyMissionView(
                    "Первый предметный срез",
                    "Начните с короткого теста, чтобы Damulab построил стартовую карту знаний.",
                    "Начать тест",
                    "/student/tests"
            );
        }
        return new DailyMissionView(
                "Поддержать учебный ритм",
                "Пройдите один короткий срез сегодня, чтобы сохранить серию активности.",
                "Продолжить",
                "/student/tests"
        );
    }

    private LastActivityView lastActivity(TestResult result, String language) {
        String subject = result.getSession().getSubject() == null
                ? "Testing Hub"
                : localized(result.getSession().getSubject().getTitleRu(), result.getSession().getSubject().getTitleKk(), language);
        String grade = result.getSession().getGrade() == null
                ? ""
                : localized(result.getSession().getGrade().getTitleRu(), result.getSession().getGrade().getTitleKk(), language);
        String detail = result.getCorrectAnswers() + " из " + result.getTotalQuestions() + " верно";
        return new LastActivityView(
                result.getSession().getId(),
                subject + (grade.isBlank() ? "" : " · " + grade),
                detail,
                result.getPercent(),
                result.getSession().getFinishedAt() == null ? result.getCreatedAt() : result.getSession().getFinishedAt()
        );
    }

    private ProgressWidgetView progress(
            long completedTests,
            List<TestResult> recentResults,
            List<KnowledgeMapItemResponse> knowledgeMap,
            List<AchievementView> achievementViews
    ) {
        int averagePercent = recentResults.isEmpty()
                ? 0
                : (int) Math.round(recentResults.stream().mapToInt(TestResult::getPercent).average().orElse(0));
        List<KnowledgeMapItemResponse> topics = knowledgeMap.stream()
                .filter(item -> item.atomicSkillId() == null)
                .toList();
        int overallMastery = topics.isEmpty()
                ? 0
                : (int) Math.round(topics.stream().mapToInt(KnowledgeMapItemResponse::masteryPercent).average().orElse(0));
        int earned = (int) achievementViews.stream().filter(AchievementView::earned).count();
        return new ProgressWidgetView(completedTests, averagePercent, overallMastery, earned);
    }

    private String localized(String ru, String kk, String language) {
        return "kk".equals(language) && kk != null && !kk.isBlank() ? kk : ru;
    }
}
