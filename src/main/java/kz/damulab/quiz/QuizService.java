package kz.damulab.quiz;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.content.Grade;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.Subject;
import kz.damulab.content.SubjectRepository;
import kz.damulab.questions.QuestionVersion;
import kz.damulab.questions.QuestionVersionRepository;
import kz.damulab.testing.AnswerCheckResult;
import kz.damulab.testing.AnswerChecker;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

@Service
public class QuizService {

    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final int DEFAULT_ROUND_SECONDS = 20;
    private static final int DEFAULT_MAX_PLAYERS = 4;
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final QuizRoomRepository rooms;
    private final QuizParticipantRepository participants;
    private final QuizRoundRepository rounds;
    private final QuizAnswerRepository answers;
    private final QuizResultRepository results;
    private final QuestionVersionRepository questionVersions;
    private final SubjectRepository subjects;
    private final GradeRepository grades;
    private final StudentProfileRepository students;
    private final AnswerChecker answerChecker;
    private final ObjectMapper objectMapper;
    private final QuizRoomEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Object timeoutLock = new Object();

    public QuizService(
            QuizRoomRepository rooms,
            QuizParticipantRepository participants,
            QuizRoundRepository rounds,
            QuizAnswerRepository answers,
            QuizResultRepository results,
            QuestionVersionRepository questionVersions,
            SubjectRepository subjects,
            GradeRepository grades,
            StudentProfileRepository students,
            AnswerChecker answerChecker,
            ObjectMapper objectMapper,
            QuizRoomEventPublisher eventPublisher,
            JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.rooms = rooms;
        this.participants = participants;
        this.rounds = rounds;
        this.answers = answers;
        this.results = results;
        this.questionVersions = questionVersions;
        this.subjects = subjects;
        this.grades = grades;
        this.students = students;
        this.answerChecker = answerChecker;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public QuizRoomResponse createRoom(String studentEmail, CreateQuizRoomRequest request) {
        StudentProfile host = findStudent(studentEmail);
        Subject subject = subjects.findById(request.getSubjectId())
                .orElseThrow(() -> new QuizException("subject_not_found"));
        Grade grade = grades.findById(request.getGradeId())
                .orElseThrow(() -> new QuizException("grade_not_found"));
        int questionCount = valueOrDefault(request.getQuestionCount(), DEFAULT_QUESTION_COUNT);
        int roundSeconds = valueOrDefault(request.getRoundSeconds(), DEFAULT_ROUND_SECONDS);
        int maxPlayers = valueOrDefault(request.getMaxPlayers(), DEFAULT_MAX_PLAYERS);
        String language = normalizeLanguage(request.getLanguage(), host.getPreferredLanguage());

        List<QuestionVersion> selected = questionVersions.findPublishedForTest(
                subject.getId(),
                grade.getId(),
                request.getDifficulty(),
                PageRequest.of(0, questionCount)
        );
        if (selected.isEmpty()) {
            throw new QuizException("published_questions_not_found");
        }

        QuizRoom room = rooms.save(new QuizRoom(
                generateCode(),
                host,
                subject,
                grade,
                language,
                request.getDifficulty(),
                selected.size(),
                roundSeconds,
                maxPlayers
        ));
        participants.save(new QuizParticipant(room, host, displayName(host), true));
        int orderNo = 1;
        for (QuestionVersion version : selected) {
            rounds.save(new QuizRound(room, version, orderNo++, BigDecimal.ONE));
        }
        eventPublisher.publish("lobby.updated", room);
        return room(room.getCode(), studentEmail);
    }

    @Transactional
    public QuizRoomResponse join(String code, String studentEmail) {
        QuizRoom room = findRoom(code);
        if (room.getStatus() != QuizRoomStatus.WAITING) {
            throw new QuizException("room_not_waiting");
        }
        StudentProfile student = findStudent(studentEmail);
        QuizParticipant existing = participants.findByRoomIdAndStudentProfileId(room.getId(), student.getId()).orElse(null);
        if (existing == null) {
            if (participants.countByRoomId(room.getId()) >= room.getMaxPlayers()) {
                throw new QuizException("room_full");
            }
            participants.save(new QuizParticipant(room, student, displayName(student), false));
        }
        eventPublisher.publish("lobby.updated", room);
        return room(room.getCode(), studentEmail);
    }

    @Transactional
    public QuizRoomResponse ready(String code, String studentEmail) {
        QuizRoom room = findRoom(code);
        QuizParticipant participant = findParticipant(room, studentEmail);
        if (room.getStatus() != QuizRoomStatus.WAITING) {
            throw new QuizException("room_not_waiting");
        }
        participant.markReady();
        eventPublisher.publish("ready.updated", room);
        return room(room.getCode(), studentEmail);
    }

    @Transactional
    public QuizRoomResponse start(String code, String studentEmail) {
        QuizRoom room = findRoom(code);
        StudentProfile student = findStudent(studentEmail);
        if (!room.getHostStudentProfile().getId().equals(student.getId())) {
            throw new QuizException("only_host_can_start");
        }
        if (room.getStatus() != QuizRoomStatus.WAITING) {
            throw new QuizException("room_not_waiting");
        }
        List<QuizParticipant> roomParticipants = participants.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId());
        if (roomParticipants.isEmpty() || roomParticipants.stream().anyMatch(participant -> !participant.isReady())) {
            throw new QuizException("participants_not_ready");
        }
        if (rounds.countByRoomId(room.getId()) == 0) {
            throw new QuizException("room_rounds_not_found");
        }
        room.start(now());
        eventPublisher.publish("room.started", room);
        return room(room.getCode(), studentEmail);
    }

    @Transactional
    public QuizRoomResponse submitAnswer(String code, String studentEmail, SubmitQuizAnswerRequest request) {
        QuizRoom room = findRoom(code);
        QuizParticipant participant = findParticipant(room, studentEmail);
        QuizRound round = rounds.findByIdAndRoomId(request.roundId(), room.getId())
                .orElseThrow(() -> new QuizException("round_not_found"));
        OffsetDateTime requestTime = now();
        if (isLate(room, round, requestTime)) {
            enforceTimeoutsLocked(room, requestTime);
            throw new QuizException("late_answer");
        }
        enforceTimeoutsLocked(room, requestTime);
        if (room.getStatus() != QuizRoomStatus.ACTIVE) {
            throw new QuizException("room_not_active");
        }
        validateRoundWindow(room, round, requestTime);
        AnswerCheckResult check = answerChecker.check(round.getQuestionVersion(), request.answer(), round.getPoints());
        String answerJson = toJson(request.answer());
        QuizAnswer existing = answers.findByRoundIdAndParticipantId(round.getId(), participant.getId()).orElse(null);
        if (existing == null) {
            if (!insertAnswerIfAbsent(round.getId(), participant.getId(), answerJson, check.correct(), check.pointsAwarded())) {
                answers.findByRoundIdAndParticipantId(round.getId(), participant.getId())
                        .orElseThrow(() -> new QuizException("answer_conflict"))
                        .replace(answerJson, check.correct(), check.pointsAwarded());
            }
        } else {
            existing.replace(answerJson, check.correct(), check.pointsAwarded());
        }
        boolean finished = finishIfComplete(room);
        eventPublisher.publish(finished ? "room.finished" : "answer.progress", room);
        return room(room.getCode(), studentEmail);
    }

    @Transactional
    public QuizRoomResponse room(String code, String studentEmail) {
        QuizRoom room = findRoom(code);
        boolean finished = enforceTimeoutsLocked(room, now());
        if (finished) {
            eventPublisher.publish("room.finished", room);
        }
        QuizParticipant current = findParticipant(room, studentEmail);
        return toRoomResponse(room, current);
    }

    @Transactional
    public QuizResultsResponse results(String code, String studentEmail) {
        QuizRoom room = findRoom(code);
        boolean finished = enforceTimeoutsLocked(room, now());
        if (finished) {
            eventPublisher.publish("room.finished", room);
        }
        QuizParticipant current = findParticipant(room, studentEmail);
        if (room.getStatus() != QuizRoomStatus.FINISHED) {
            throw new QuizException("room_not_finished");
        }
        List<QuizParticipantResultResponse> rows = results.findByRoomIdOrderByScoreDescPercentDescCreatedAtAsc(room.getId()).stream()
                .map(result -> new QuizParticipantResultResponse(
                        result.getParticipant().getId(),
                        result.getParticipant().getDisplayName(),
                        result.getParticipant().getId().equals(current.getId()),
                        result.getTotalQuestions(),
                        result.getCorrectAnswers(),
                        result.getScore(),
                        result.getMaxScore(),
                        result.getPercent()
                ))
                .toList();
        return new QuizResultsResponse(room.getCode(), room.getStatus().name().toLowerCase(Locale.ROOT), rows);
    }

    @Transactional
    public void enforceActiveRoomTimeouts() {
        OffsetDateTime now = now();
        for (QuizRoom room : rooms.findByStatus(QuizRoomStatus.ACTIVE)) {
            boolean finished = enforceTimeoutsLocked(room, now);
            if (finished) {
                eventPublisher.publish("room.finished", room);
            }
        }
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

    private boolean finishIfComplete(QuizRoom room) {
        long expected = participants.countByRoomId(room.getId()) * rounds.countByRoomId(room.getId());
        if (expected == 0 || answers.countByRoundRoomId(room.getId()) < expected || results.existsByRoomId(room.getId())) {
            return false;
        }
        List<QuizRound> roomRounds = rounds.findByRoomIdOrderByOrderNoAsc(room.getId());
        BigDecimal maxScore = roomRounds.stream()
                .map(QuizRound::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (QuizParticipant participant : participants.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId())) {
            List<QuizAnswer> participantAnswers = answers.findByParticipantIdAndRoundRoomId(participant.getId(), room.getId());
            int correct = 0;
            BigDecimal score = BigDecimal.ZERO;
            for (QuizAnswer answer : participantAnswers) {
                if (answer.isCorrect()) {
                    correct++;
                }
                score = score.add(answer.getPointsAwarded());
            }
            int percent = maxScore.compareTo(BigDecimal.ZERO) == 0
                    ? 0
                    : score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 0, RoundingMode.HALF_UP).intValue();
            results.save(new QuizResult(room, participant, roomRounds.size(), correct, score, maxScore, percent));
        }
        room.finish(now());
        return true;
    }

    private boolean enforceTimeouts(QuizRoom room, OffsetDateTime now) {
        if (room.getStatus() != QuizRoomStatus.ACTIVE || room.getStartedAt() == null || results.existsByRoomId(room.getId())) {
            return false;
        }
        boolean timeoutSaved = false;
        List<QuizParticipant> roomParticipants = participants.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId());
        for (QuizRound round : rounds.findByRoomIdOrderByOrderNoAsc(room.getId())) {
            if (now.isBefore(roundEndsAt(room, round))) {
                continue;
            }
            for (QuizParticipant participant : roomParticipants) {
                if (answers.findByRoundIdAndParticipantId(round.getId(), participant.getId()).isEmpty()) {
                    timeoutSaved = insertTimeoutAnswerIfAbsent(round.getId(), participant.getId()) || timeoutSaved;
                }
            }
        }
        if (timeoutSaved) {
            eventPublisher.publish("round.timeout", room);
        }
        return finishIfComplete(room);
    }

    private boolean enforceTimeoutsLocked(QuizRoom room, OffsetDateTime now) {
        synchronized (timeoutLock) {
            return enforceTimeouts(room, now);
        }
    }

    private boolean insertTimeoutAnswerIfAbsent(Long roundId, Long participantId) {
        return insertAnswerIfAbsent(roundId, participantId, "{}", false, BigDecimal.ZERO);
    }

    private boolean insertAnswerIfAbsent(
            Long roundId,
            Long participantId,
            String answerJson,
            boolean correct,
            BigDecimal pointsAwarded
    ) {
        try {
            return jdbcTemplate.update("""
                    insert into quiz_answers (round_id, participant_id, answer_json, is_correct, points_awarded, answered_at)
                    values (?, ?, ?, ?, ?, current_timestamp)
                    """, roundId, participantId, answerJson, correct, pointsAwarded == null ? BigDecimal.ZERO : pointsAwarded) > 0;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    private QuizRoomResponse toRoomResponse(QuizRoom room, QuizParticipant current) {
        OffsetDateTime responseTime = now();
        List<QuizAnswer> roomAnswers = answers.findByRoundRoomId(room.getId());
        Map<Long, Integer> answeredByParticipant = new LinkedHashMap<>();
        List<Long> currentAnsweredRounds = new ArrayList<>();
        for (QuizAnswer answer : roomAnswers) {
            Long participantId = answer.getParticipant().getId();
            answeredByParticipant.put(participantId, answeredByParticipant.getOrDefault(participantId, 0) + 1);
            if (current != null && current.getId().equals(participantId)) {
                currentAnsweredRounds.add(answer.getRound().getId());
            }
        }
        List<QuizParticipantResponse> participantResponses = participants.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId()).stream()
                .map(participant -> new QuizParticipantResponse(
                        participant.getId(),
                        participant.getDisplayName(),
                        participant.getStudentProfile().getId().equals(room.getHostStudentProfile().getId()),
                        participant.isReady(),
                        current != null && participant.getId().equals(current.getId()),
                        answeredByParticipant.getOrDefault(participant.getId(), 0)
                ))
                .toList();
        List<QuizRoundResponse> roundResponses = rounds.findByRoomIdOrderByOrderNoAsc(room.getId()).stream()
                .map(round -> toRoundResponse(room, round, currentAnsweredRounds.contains(round.getId()), room.getLanguage(), responseTime))
                .toList();
        return new QuizRoomResponse(
                room.getId(),
                room.getCode(),
                room.getStatus().name().toLowerCase(Locale.ROOT),
                current != null && current.getStudentProfile().getId().equals(room.getHostStudentProfile().getId()),
                current == null ? null : current.getId(),
                room.getSubject() == null ? null : room.getSubject().getId(),
                room.getSubject() == null ? null : localized(room.getSubject().getTitleRu(), room.getSubject().getTitleKk(), room.getLanguage()),
                room.getGrade() == null ? null : room.getGrade().getId(),
                room.getGrade() == null ? null : localized(room.getGrade().getTitleRu(), room.getGrade().getTitleKk(), room.getLanguage()),
                room.getLanguage(),
                room.getDifficulty(),
                room.getQuestionCount(),
                room.getRoundSeconds(),
                room.getMaxPlayers(),
                responseTime,
                activeRoundId(room, roundResponses, responseTime),
                room.getCreatedAt(),
                room.getStartedAt(),
                room.getFinishedAt(),
                participantResponses,
                roundResponses
        );
    }

    private QuizRoundResponse toRoundResponse(QuizRoom room, QuizRound round, boolean answered, String language, OffsetDateTime responseTime) {
        QuestionVersion version = round.getQuestionVersion();
        return new QuizRoundResponse(
                round.getId(),
                round.getOrderNo(),
                version.getType().name(),
                localized(version.getBodyRu(), version.getBodyKk(), language),
                localized(version.getTopic().getTitleRu(), version.getTopic().getTitleKk(), language),
                version.getAtomicSkill() == null ? null : localized(version.getAtomicSkill().getTitleRu(), version.getAtomicSkill().getTitleKk(), language),
                version.getDifficulty(),
                round.getPoints(),
                room.getStartedAt() == null ? null : roundStartsAt(room, round),
                room.getStartedAt() == null ? null : roundEndsAt(room, round),
                room.getStartedAt() != null && !responseTime.isBefore(roundEndsAt(room, round)),
                answerChecker.choices(version, language),
                answerChecker.matchingLeft(version, language),
                answerChecker.matchingRight(version, language),
                answerChecker.fillPlaceholders(version),
                answered
        );
    }

    private Long activeRoundId(QuizRoom room, List<QuizRoundResponse> roundResponses, OffsetDateTime now) {
        if (room.getStatus() != QuizRoomStatus.ACTIVE || room.getStartedAt() == null) {
            return null;
        }
        return roundResponses.stream()
                .filter(round -> round.startsAt() != null && round.endsAt() != null)
                .filter(round -> !now.isBefore(round.startsAt()) && now.isBefore(round.endsAt()))
                .map(QuizRoundResponse::id)
                .findFirst()
                .orElse(null);
    }

    private void validateRoundWindow(QuizRoom room, QuizRound round, OffsetDateTime now) {
        OffsetDateTime startsAt = roundStartsAt(room, round);
        OffsetDateTime endsAt = roundEndsAt(room, round);
        if (now.isBefore(startsAt)) {
            throw new QuizException("round_not_open");
        }
        if (!now.isBefore(endsAt)) {
            throw new QuizException("late_answer");
        }
    }

    private boolean isLate(QuizRoom room, QuizRound round, OffsetDateTime now) {
        return room.getStartedAt() != null && !now.isBefore(roundEndsAt(room, round));
    }

    private OffsetDateTime roundStartsAt(QuizRoom room, QuizRound round) {
        return room.getStartedAt().plusSeconds((long) (round.getOrderNo() - 1) * room.getRoundSeconds());
    }

    private OffsetDateTime roundEndsAt(QuizRoom room, QuizRound round) {
        return roundStartsAt(room, round).plusSeconds(room.getRoundSeconds());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private QuizRoom findRoom(String code) {
        return rooms.findByCodeIgnoreCase(normalizeCode(code))
                .orElseThrow(() -> new QuizException("room_not_found"));
    }

    private QuizParticipant findParticipant(QuizRoom room, String studentEmail) {
        StudentProfile student = findStudent(studentEmail);
        return participants.findByRoomIdAndStudentProfileId(room.getId(), student.getId())
                .orElseThrow(() -> new QuizException("participant_not_found"));
    }

    private StudentProfile findStudent(String email) {
        return students.findByUserEmailIgnoreCase(email)
                .orElseThrow(() -> new QuizException("student_profile_not_found"));
    }

    private String displayName(StudentProfile student) {
        return student.getUser().getFullName();
    }

    private String normalizeLanguage(String requested, String fallback) {
        String value = requested == null || requested.isBlank() ? fallback : requested;
        return "kk".equals(value) ? "kk" : "ru";
    }

    private int valueOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new QuizException("room_code_required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String generateCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder("QZ");
            for (int i = 0; i < 4; i++) {
                code.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)]);
            }
            String value = code.toString();
            if (!rooms.existsByCodeIgnoreCase(value)) {
                return value;
            }
        }
        throw new QuizException("room_code_generation_failed");
    }

    private JsonNode objectNode(Object value) {
        return objectMapper.valueToTree(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new QuizException("answer_payload_invalid");
        }
    }

    private String localized(String ru, String kk, String language) {
        return "kk".equals(language) && kk != null && !kk.isBlank() ? kk : ru;
    }
}
