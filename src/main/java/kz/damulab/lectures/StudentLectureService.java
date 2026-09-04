package kz.damulab.lectures;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.content.Subject;
import kz.damulab.content.SubjectRepository;
import kz.damulab.content.Topic;
import kz.damulab.questions.QuestionVersion;
import kz.damulab.testing.AnswerChecker;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

/**
 * Ученический сценарий работы с лекциями.
 *
 * <p>Сервис отделён от административного {@link LectureService}: администратор управляет
 * содержимым и публикацией, а здесь находятся только каталог, личный прогресс и
 * проверка контрольных вопросов.</p>
 */
@Service
public class StudentLectureService {

    private final LectureRepository lectures;
    private final LectureCheckpointRepository checkpoints;
    private final StudentLectureProgressRepository progressRepository;
    private final StudentProfileRepository students;
    private final SubjectRepository subjects;
    private final LectureService lectureService;
    private final AnswerChecker answerChecker;
    private final ObjectMapper objectMapper;

    public StudentLectureService(
            LectureRepository lectures,
            LectureCheckpointRepository checkpoints,
            StudentLectureProgressRepository progressRepository,
            StudentProfileRepository students,
            SubjectRepository subjects,
            LectureService lectureService,
            AnswerChecker answerChecker,
            ObjectMapper objectMapper
    ) {
        this.lectures = lectures;
        this.checkpoints = checkpoints;
        this.progressRepository = progressRepository;
        this.students = students;
        this.subjects = subjects;
        this.lectureService = lectureService;
        this.answerChecker = answerChecker;
        this.objectMapper = objectMapper;
    }

    /** Возвращает плитки только тех предметов, где есть опубликованные лекции. */
    @Transactional(readOnly = true)
    public List<LectureSubjectView> listSubjects() {
        return subjects.findWithPublishedLectures();
    }

    /**
     * Возвращает лекции выбранного предмета с личными статусами ученика.
     * Отсутствующая запись прогресса отображается как NOT_STARTED.
     */
    @Transactional(readOnly = true)
    public StudentLectureSubjectPageView subjectPage(String studentEmail, Long subjectId) {
        StudentProfile student = findStudent(studentEmail);
        Subject subject = subjects.findById(subjectId)
                .orElseThrow(() -> new LectureException("subject_not_found"));
        List<Lecture> publishedLectures = lectures.findPublishedBySubjectIdOrderByCreatedAtDesc(subjectId);

        List<Long> lectureIds = publishedLectures.stream().map(Lecture::getId).toList();
        Map<Long, StudentLectureProgress> progressByLecture = loadProgress(student.getId(), lectureIds);
        List<StudentLectureListItemView> items = publishedLectures.stream()
                .map(lecture -> toListItem(lecture, progressByLecture.get(lecture.getId())))
                .toList();

        return new StudentLectureSubjectPageView(
                subject.getId(),
                subject.getTitleRu(),
                subject.getTitleKk(),
                items
        );
    }

    /**
     * Открывает опубликованную лекцию и при первом посещении создаёт статус IN_PROGRESS.
     * Завершённый статус не изменяется при повторном чтении.
     */
    @Transactional
    public StudentLectureReaderView openLecture(String studentEmail, Long lectureId, String language) {
        StudentProfile student = findStudent(studentEmail);
        Lecture lecture = findPublishedLecture(lectureId);
        StudentLectureProgress progress = progressRepository
                .findByStudentProfileIdAndLectureId(student.getId(), lectureId)
                .orElseGet(() -> progressRepository.save(new StudentLectureProgress(student, lecture)));
        return toReaderView(lecture, progress, language);
    }

    /**
     * Возвращает безопасные модели вопросов для разбора отправленной HTML-формы.
     * Метод повторно проверяет публикацию лекции, поэтому произвольный идентификатор
     * нельзя использовать для чтения скрытых вопросов.
     */
    @Transactional(readOnly = true)
    public List<LectureCheckpointQuestionView> questionsForSubmission(Long lectureId, String language) {
        Lecture lecture = findPublishedLecture(lectureId);
        return questionViews(lecture, language);
    }

    /**
     * Проверяет одну попытку теста. Попыток может быть сколько угодно; успешной
     * считается только попытка, где каждый контрольный вопрос решён правильно.
     */
    @Transactional
    public LectureCheckpointAttemptResult submitCheckpointAttempt(
            String studentEmail,
            Long lectureId,
            Map<Long, JsonNode> answers
    ) {
        StudentProfile student = findStudent(studentEmail);
        Lecture lecture = findPublishedLecture(lectureId);
        StudentLectureProgress progress = findOrStartProgress(student, lecture);
        List<LectureCheckpoint> lectureCheckpoints = checkpoints
                .findByLectureVersionIdOrderBySortOrderAscIdAsc(lecture.getCurrentVersion().getId());
        if (lectureCheckpoints.isEmpty()) {
            throw new LectureException("lecture_checkpoints_not_found");
        }

        int correctCount = 0;
        for (LectureCheckpoint checkpoint : lectureCheckpoints) {
            JsonNode answer = answers.get(checkpoint.getId());
            if (answer == null) {
                answer = objectMapper.createObjectNode();
            }
            if (answerChecker.check(checkpoint.getQuestionVersion(), answer, BigDecimal.ONE).correct()) {
                correctCount++;
            }
        }

        boolean passed = correctCount == lectureCheckpoints.size();
        progress.registerCheckpointAttempt(passed);
        return new LectureCheckpointAttemptResult(passed, correctCount, lectureCheckpoints.size());
    }

    /**
     * Завершает лекцию. Если у текущей версии есть контрольные вопросы, сервис
     * обязательно проверяет успешную попытку на сервере.
     */
    @Transactional
    public void completeLecture(String studentEmail, Long lectureId) {
        StudentProfile student = findStudent(studentEmail);
        Lecture lecture = findPublishedLecture(lectureId);
        StudentLectureProgress progress = findOrStartProgress(student, lecture);
        boolean hasCheckpoints = checkpoints.countByLectureVersionId(lecture.getCurrentVersion().getId()) > 0;
        if (hasCheckpoints && progress.getCheckpointPassedAt() == null) {
            throw new LectureException("lecture_checkpoint_required");
        }
        progress.complete();
    }

    /** Собирает полную модель reader-страницы без раскрытия правильных ответов. */
    private StudentLectureReaderView toReaderView(
            Lecture lecture,
            StudentLectureProgress progress,
            String language
    ) {
        Topic topic = lecture.getCurrentVersion().getTopic();
        Subject subject = topic.getSubject();
        List<LectureCheckpointQuestionView> questions = questionViews(lecture, language);
        boolean checkpointPassed = progress.getCheckpointPassedAt() != null;
        boolean canComplete = questions.isEmpty()
                || checkpointPassed
                || progress.getStatus() == StudentLectureStatus.DONE;
        return new StudentLectureReaderView(
                lectureService.getPublishedLecture(lecture.getId()),
                subject.getId(),
                subject.getTitleRu(),
                subject.getTitleKk(),
                progress.getStatus().apiValue(),
                checkpointPassed,
                canComplete,
                questions
        );
    }

    /** Преобразует контрольные вопросы в локализованные данные формы. */
    private List<LectureCheckpointQuestionView> questionViews(Lecture lecture, String language) {
        Topic lectureTopic = lecture.getCurrentVersion().getTopic();
        return checkpoints.findByLectureVersionIdOrderBySortOrderAscIdAsc(lecture.getCurrentVersion().getId())
                .stream()
                .map(checkpoint -> toQuestionView(checkpoint, lectureTopic, language))
                .toList();
    }

    /** Собирает один вопрос, используя уже проверенные представления вариантов ответа. */
    private LectureCheckpointQuestionView toQuestionView(
            LectureCheckpoint checkpoint,
            Topic lectureTopic,
            String language
    ) {
        QuestionVersion version = checkpoint.getQuestionVersion();
        return new LectureCheckpointQuestionView(
                checkpoint.getId(),
                checkpoint.getSortOrder(),
                version.getType().name(),
                localized(version.getBodyRu(), version.getBodyKk(), language),
                localized(lectureTopic.getTitleRu(), lectureTopic.getTitleKk(), language),
                answerChecker.choices(version, language),
                answerChecker.matchingLeft(version, language),
                answerChecker.matchingRight(version, language),
                answerChecker.fillPlaceholders(version)
        );
    }

    /** Собирает строку списка с вычисленным начальным статусом. */
    private StudentLectureListItemView toListItem(Lecture lecture, StudentLectureProgress progress) {
        LectureVersion version = lecture.getCurrentVersion();
        StudentLectureStatus status = progress == null ? StudentLectureStatus.NOT_STARTED : progress.getStatus();
        return new StudentLectureListItemView(
                lecture.getId(),
                version.getTitleRu(),
                version.getTitleKk(),
                version.getTopic().getTitleRu(),
                version.getTopic().getTitleKk(),
                lecture.getCreatedAt(),
                status.apiValue()
        );
    }

    /** Загружает прогресс списком и индексирует его по идентификатору лекции. */
    private Map<Long, StudentLectureProgress> loadProgress(Long studentId, List<Long> lectureIds) {
        Map<Long, StudentLectureProgress> result = new HashMap<>();
        if (lectureIds.isEmpty()) {
            return result;
        }
        for (StudentLectureProgress progress : progressRepository
                .findByStudentProfileIdAndLectureIdIn(studentId, lectureIds)) {
            result.put(progress.getLecture().getId(), progress);
        }
        return result;
    }

    /** Находит или создаёт начатый прогресс для изменяющей операции. */
    private StudentLectureProgress findOrStartProgress(StudentProfile student, Lecture lecture) {
        return progressRepository.findByStudentProfileIdAndLectureId(student.getId(), lecture.getId())
                .orElseGet(() -> progressRepository.save(new StudentLectureProgress(student, lecture)));
    }

    /** Находит опубликованную лекцию; черновики и архив скрываются как отсутствующие. */
    private Lecture findPublishedLecture(Long lectureId) {
        return lectures.findPublishedWithTopic(lectureId)
                .orElseThrow(() -> new LectureException("lecture_not_found"));
    }

    /** Находит профиль текущего ученика по имени авторизованного пользователя. */
    private StudentProfile findStudent(String studentEmail) {
        return students.findByUserEmailIgnoreCase(studentEmail)
                .orElseThrow(() -> new LectureException("student_not_found"));
    }

    /** Выбирает казахский текст только для языка kk, иначе использует русский. */
    private String localized(String ru, String kk, String language) {
        if ("kk".equals(language) && kk != null && !kk.isBlank()) {
            return kk;
        }
        return ru;
    }
}
