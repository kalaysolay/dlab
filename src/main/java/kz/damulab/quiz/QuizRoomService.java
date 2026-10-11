package kz.damulab.quiz;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import kz.damulab.config.DamulabQuizProperties;
import kz.damulab.content.*;
import kz.damulab.users.*;
import kz.damulab.quiz.catalog.*;
import kz.damulab.quiz.engine.*;
import org.springframework.dao.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Команды новых комнат. Строка комнаты сериализует lifecycle/answer; согласованный снимок
 * банка читается в REPEATABLE_READ. Повтор после коллизии/serialization failure начинает
 * новую транзакцию, а события выходят только после её commit (ARCHITECTURE.md §§ 3–4).
 */
@Service
public class QuizRoomService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuizRoomService.class);
    private final QuizRoomRepository rooms;
    private final QuizParticipantRepository participants;
    private final QuizRoundRepository rounds;
    private final QuizAnswerRepository answers;
    private final StudentProfileRepository students;
    private final GradeRepository grades;
    private final QuizRoomCreationKeyRepository keys;
    private final QuizGameAvailabilityPolicy availability;
    private final QuizAudienceResolver audience;
    private final QuizSubjectGameConfigRepository subjectConfigs;
    private final SubjectRepository subjects;
    private final kz.damulab.questions.QuestionVersionRepository questionVersions;
    private final Map<QuizGameType, QuizRoundProvider> providers;
    private final QuizRoundFactory factory;
    private final QuizAnswerEvaluator evaluator;
    private final QuizRoundAnswerChecker checker;
    private final QuizCurrentRoundReader reader;
    private final QuizGameResultService results;
    private final QuizSnapshotJson json;
    private final QuizRoomEventPublisher events;
    private final QuizRoomCode codes;
    private final QuizRoomSubscriptions subscriptions;
    private final DamulabQuizProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /** Подключает общие policies движка и транзакционный менеджер без запуска игры. */
    public QuizRoomService(QuizRoomRepository rooms, QuizParticipantRepository participants, QuizRoundRepository rounds,
            QuizAnswerRepository answers, StudentProfileRepository students, GradeRepository grades,
            QuizRoomCreationKeyRepository keys, QuizGameAvailabilityPolicy availability, QuizAudienceResolver audience,
            List<QuizRoundProvider> providers, QuizRoundFactory factory, QuizAnswerEvaluator evaluator,
            QuizRoundAnswerChecker checker, QuizCurrentRoundReader reader, QuizGameResultService results,
            QuizSnapshotJson json, QuizRoomEventPublisher events, QuizRoomCode codes, DamulabQuizProperties properties,
            Clock clock, PlatformTransactionManager manager, QuizRoomSubscriptions subscriptions, QuizSubjectGameConfigRepository subjectConfigs,
            SubjectRepository subjects, kz.damulab.questions.QuestionVersionRepository questionVersions) {
        this.rooms = rooms; this.participants = participants; this.rounds = rounds; this.answers = answers;
        this.students = students; this.grades = grades; this.keys = keys; this.availability = availability;
        this.audience = audience; this.providers = providers.stream().collect(Collectors.toMap(QuizRoundProvider::gameType, Function.identity()));
        this.factory = factory; this.evaluator = evaluator; this.checker = checker; this.reader = reader;
        this.results = results; this.json = json; this.events = events; this.codes = codes;
        this.properties = properties; this.clock = clock;
        this.subscriptions = subscriptions;
        this.subjectConfigs = subjectConfigs;
        this.subjects = subjects;
        this.questionVersions = questionVersions;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /** Создаёт предметную комнату с параметрами ученика, повтор формы не создаёт вторую комнату. */
    public QuizGameRoomResponse createCustom(String email, QuizGameRoomRequest.CustomCreate request) {
        if (request == null || request.subjectId() == null || request.gradeId() == null
                || request.idempotencyKey() == null || request.idempotencyKey().isBlank() || request.idempotencyKey().length() > 128
                || request.language() == null || !Set.of("ru", "kk").contains(request.language()))
            throw new QuizEngineException("REQUEST_INVALID");
        int count = request.questionCount() == null ? 15 : request.questionCount();
        int seconds = request.roundSeconds() == null ? 10 : request.roundSeconds();
        String keyHash = hash(request.idempotencyKey());
        String payloadHash = hash(json.write(Arrays.asList("CUSTOM_SUBJECT", request.subjectId(), request.gradeId(), request.language(), count, seconds)));
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                return transact(() -> {
                    var host = students.findLockedByEmail(email).orElseThrow(() -> new QuizEngineException("PROFILE_GRADE_REQUIRED"));
                    var existing = keys.findByStudentProfileIdAndKeyHash(host.getId(), keyHash);
                    if (existing.isPresent()) {
                        if (!existing.get().getPayloadHash().equals(payloadHash)) throw new QuizEngineException("IDEMPOTENCY_CONFLICT");
                        return response(existing.get().getRoom(), host);
                    }
                    var subject = subjects.findById(request.subjectId()).orElseThrow(() -> new QuizEngineException("REQUEST_INVALID"));
                    var grade = grades.findById(request.gradeId()).orElseThrow(() -> new QuizEngineException("INVALID_GRADE"));
                    String code = QuizRoomCode.normalize(codes.generate());
                    if (rooms.existsByCodeIgnoreCase(code)) throw new CodeCollision();
                    var room = new QuizRoom(code, host, subject, grade, request.language(), null, count, seconds, 4);
                    room.initializeCustom(now().plus(properties.getWaitingTimeout()));
                    var member = new QuizParticipant(room, host, host.getUser().getFullName(), true);
                    member.markReady(0);
                    validateSettings(room); validateContent(room, List.of(member));
                    rooms.saveAndFlush(room); participants.saveAndFlush(member);
                    keys.saveAndFlush(new QuizRoomCreationKey(host, keyHash, payloadHash, room));
                    events.publish("room.created", room);
                    return response(room, host);
                });
            } catch (CodeCollision | DataIntegrityViolationException ex) {
                if (attempt == 9) throw new QuizEngineException("ROOM_CODE_EXHAUSTED");
            }
        }
        throw new QuizEngineException("ROOM_CODE_EXHAUSTED");
    }

    /** Отдаёт только предметы/классы с пригодными опубликованными вопросами, одним запросом к банку. */
    public QuizSetupCatalog setupCatalog() {
        return transact(() -> {
            var available = questionVersions.findPublishedForQuizSource(null).stream().filter(QuizContentEligibility::subjectComplete).toList();
            var counts = new LinkedHashMap<Long, Map<Long, Long>>();
            for (var version : available) {
                var byGrade = counts.computeIfAbsent(version.getSubject().getId(), ignored -> new LinkedHashMap<>());
                version.getGradeLinks().stream().map(g -> g.getGrade().getId()).distinct().forEach(id -> byGrade.merge(id, 1L, Long::sum));
            }
            var gradeList = grades.findAllByOrderByGradeNoAsc();
            var options = subjects.findAllByOrderByTitleRuAsc().stream().filter(s -> counts.containsKey(s.getId())).map(s -> {
                var byGrade = counts.get(s.getId());
                var choices = gradeList.stream().filter(g -> byGrade.containsKey(g.getId()))
                        .map(g -> new QuizSetupGradeOption(g.getId(), g.getGradeNo(), g.getTitleRu(), g.getTitleKk(), byGrade.get(g.getId()))).toList();
                return new QuizSetupSubjectOption(s.getId(), s.getCode(), s.getTitleRu(), s.getTitleKk(), available.stream().filter(v -> v.getSubject().getId().equals(s.getId())).count(), choices);
            }).filter(s -> !s.grades().isEmpty()).toList();
            return new QuizSetupCatalog("school_subject", options);
        });
    }

    /** Диапазоны самостоятельной викторины общие для create/settings/start, вместимость не выбирается. */
    public static void validateCustomSettings(QuizRoom room) {
        if (room.getSubject() == null || room.getAudienceGradeNo() == null || room.getQuestionCount() < 1 || room.getQuestionCount() > 50
                || room.getRoundSeconds() < 5 || room.getRoundSeconds() > 120 || room.getMaxPlayers() != 4
                || !Set.of("ru", "kk").contains(room.getLanguage())) throw new QuizEngineException("SETTINGS_INVALID");
    }

    /** Фиксирует текущую публикацию и host; повтор того же ключа возвращает ту же комнату даже после скрытия игры. */
    public QuizGameRoomResponse create(String email, QuizGameRoomRequest.Create request) {
        if (request == null || !QuizGameValidation.validCode(request.gameCode())
                || request.idempotencyKey() == null || request.idempotencyKey().isBlank() || request.idempotencyKey().length() > 128)
            throw new QuizEngineException("REQUEST_INVALID");
        String keyHash = hash(request.idempotencyKey());
        String payloadHash = hash(json.write(Arrays.asList(request.gameCode(), request.gradeId(), request.questionCount(), request.roundSeconds())));
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                return transact(() -> {
                    var host = students.findLockedByEmail(email).orElseThrow(() -> new QuizEngineException("PROFILE_GRADE_REQUIRED"));
                    var existing = keys.findByStudentProfileIdAndKeyHash(host.getId(), keyHash);
                    if (existing.isPresent()) {
                        if (!existing.get().getPayloadHash().equals(payloadHash)) throw new QuizEngineException("IDEMPOTENCY_CONFLICT");
                        return response(existing.get().getRoom(), host);
                    }
                    QuizGameVersion version;
                    try { version = availability.requireNewRoom(request.gameCode()); }
                    catch (ContentValidationException ex) { throw new QuizEngineException("GAME_NOT_FOUND"); }
                    String code = QuizRoomCode.normalize(codes.generate());
                    if (rooms.existsByCodeIgnoreCase(code)) throw new CodeCollision();
                    int count = request.questionCount() == null ? version.getDefaultQuestionCount() : request.questionCount();
                    int seconds = request.roundSeconds() == null ? version.getDefaultRoundSeconds() : request.roundSeconds();
                    var subjectConfig = version.getGameType() == QuizGameType.SUBJECT_QUIZ ? subjectConfigs.findById(version.getId()).orElseThrow(() -> new QuizEngineException("GAME_CONFIGURATION_INVALID")) : null;
                    var room = new QuizRoom(code, host, subjectConfig == null ? null : subjectConfig.getSubject(), null,
                            host.getPreferredLanguage(), subjectConfig == null ? null : subjectConfig.getDifficulty(), count, seconds, version.getMaxPlayers());
                    room.attachGameVersion(version, now().plus(properties.getWaitingTimeout()));
                    room.initializeAudienceGrade(selectedGrade(version, host, request.gradeId()));
                    var member = new QuizParticipant(room, host, host.getUser().getFullName(), true);
                    member.markReady(room.getSettingsRevision());
                    validateSettings(room); validateContent(room, List.of(member));
                    rooms.saveAndFlush(room); participants.saveAndFlush(member);
                    keys.saveAndFlush(new QuizRoomCreationKey(host, keyHash, payloadHash, room));
                    events.publish("room.created", room);
                    return response(room, host);
                });
            } catch (CodeCollision | DataIntegrityViolationException ex) {
                // PostgreSQL unique failure откатывает всю попытку, включая host/key. Продолжать её нельзя.
                if (attempt == 9) throw new QuizEngineException("ROOM_CODE_EXHAUSTED");
            }
        }
        throw new QuizEngineException("ROOM_CODE_EXHAUSTED");
    }

    /** Возвращает только разрешённые ID классов закреплённой версии после проверки membership текущего аккаунта. */
    public Set<Long> allowedGradeIds(String code, String email) {
        return transact(() -> {
            var room=find(code,false);
            member(room,student(email));
            if (room.getKind() == QuizRoomKind.CUSTOM_SUBJECT)
                return setupCatalog().subjects().stream().filter(s -> s.id().equals(room.getSubject().getId()))
                        .flatMap(s -> s.grades().stream()).map(QuizSetupGradeOption::id).collect(Collectors.toSet());
            return room.getGameVersion() == null ? Set.of() : Set.copyOf(room.getGameVersion().getGradeIds());
        });
    }
    /** Читает только состояние участника и догоняет серверное расписание без клиентских таймеров. */
    public QuizGameRoomResponse state(String code, String email) {
        return transact(() -> { var room = find(code, true); var student = student(email); member(room, student); if (room.isPlayable()) advance(room, now()); return response(room, student); });
    }
    /** Ограниченное превью доступно ученику до join; никаких ID участников, условий или ответов. */
    public QuizGameRoomResponse.Preview preview(String code, String email) {
        return transact(() -> {
            var room = find(code, false); requireGame(room); var student = student(email);
            var members = members(room); String blocker = joinBlocker(room, student, members);
            return new QuizGameRoomResponse.Preview(room.getCode(), title(room), room.getHostStudentProfile().getUser().getFullName(),
                    members.size(), room.getMaxPlayers(), effectiveStatus(room).name().toLowerCase(Locale.ROOT), blocker == null, blocker);
        });
    }
    /** Добавляет гостя один раз под lock; повтор существующего участия восстанавливает ACTIVE/FINISHED. */
    public QuizGameRoomResponse join(String code, String email) {
        try { return command(code, email, (room, student) -> {
            if (participants.findByRoomIdAndStudentProfileId(room.getId(), student.getId()).isPresent()) {
                advance(room, now()); return response(room, student);
            }
            var members = members(room); String blocker = joinBlocker(room, student, members);
            if (blocker != null) throw new QuizEngineException(blocker);
            participants.saveAndFlush(new QuizParticipant(room, student, student.getUser().getFullName(), false));
            events.publish("room.participant_joined", room); return response(room, student);
        }); } catch (QuizEngineException ex) {
            // Конфликт вместимости уже откатил транзакцию; чужой ввод и аккаунт не попадают в журнал.
            if("ROOM_FULL".equals(ex.getCode())) log.warn("quiz_command_rejected action=join code=ROOM_FULL");
            throw ex;
        }
    }
    /** Гость подтверждает актуальную revision или снимает готовность; host всегда готов. */
    public QuizGameRoomResponse ready(String code, String email, QuizGameRoomRequest.Ready request) {
        return command(code, email, (room, student) -> {
            requireWaiting(room); var member = member(room, student);
            if (request == null || request.settingsRevision() != room.getSettingsRevision()) throw new QuizEngineException("SETTINGS_REVISION_CONFLICT");
            audience.resolve(room, members(room));
            if (isHost(room, student) || request.ready()) member.markReady(room.getSettingsRevision()); else member.resetReady();
            events.publish("room.readiness_changed", room); return response(room, student);
        });
    }
    /** Меняет только разрешённые настройки; при отказе транзакция сохраняет прежние правила и ready. */
    public QuizGameRoomResponse settings(String code, String email, QuizGameRoomRequest.Settings request) {
        return command(code, email, (room, student) -> {
            requireHost(room, student); requireWaiting(room);
            if (request == null || request.settingsRevision() != room.getSettingsRevision()) throw new QuizEngineException("SETTINGS_REVISION_CONFLICT");
            int count = request.questionCount() == null ? room.getQuestionCount() : request.questionCount();
            int seconds = request.roundSeconds() == null ? room.getRoundSeconds() : request.roundSeconds();
            Integer grade = request.gradeId() == null ? room.getAudienceGradeNo() : room.getKind() == QuizRoomKind.CUSTOM_SUBJECT
                    ? grades.findById(request.gradeId()).orElseThrow(() -> new QuizEngineException("INVALID_GRADE")).getGradeNo()
                    : selectedGrade(room.getGameVersion(), student, request.gradeId());
            if (!Objects.equals(grade, room.getAudienceGradeNo()) || count != room.getQuestionCount() || seconds != room.getRoundSeconds()) {
                if (room.getKind() == QuizRoomKind.CUSTOM_SUBJECT && request.gradeId() != null)
                    room.changeCustomGrade(grades.findById(request.gradeId()).orElseThrow(() -> new QuizEngineException("INVALID_GRADE")));
                room.changeSettings(grade, Set.of(), count, seconds); validateSettings(room); var members = members(room); validateContent(room, members);
                for (var participant : members) {
                    if (isHost(room, participant.getStudentProfile())) participant.markReady(room.getSettingsRevision()); else participant.resetReady();
                }
                events.publish("room.settings_changed", room);
            }
            return response(room, student);
        });
    }
    /** Атомарно проверяет host/ready/аудиторию, создаёт все снимки и запускает общее расписание ровно один раз. */
    public QuizGameRoomResponse start(String code, String email) {
        try { return command(code, email, (room, student) -> {
            requireHost(room, student);
            if (room.getStatus() == QuizRoomStatus.ACTIVE || room.getStatus() == QuizRoomStatus.FINISHED) {
                events.repeatedStart(room); return response(room, student);
            }
            requireWaiting(room);
            if (members(room).stream().anyMatch(p -> !p.isReady() || !Objects.equals(p.getReadyRevision(), room.getSettingsRevision())))
                throw new QuizEngineException("PARTICIPANT_NOT_READY");
            requireSource(room); factory.create(room.getCode());
            // Формирование может занять время: срок проверяется ещё раз перед фиксацией startedAt,
            // а отказ откатывает даже уже сохранённый batch, оставляя scheduler право EXPIRED.
            var startedAt = now();
            if (!startedAt.isBefore(room.getExpiresAt())) throw new QuizEngineException("ROOM_EXPIRED");
            room.start(startedAt);
            events.publish("room.started", room); return response(room, student);
        }); } catch (QuizEngineException ex) {
            // Только стабильный внешний код: исходный ввод, email, снимки и текст исключения не журналируются.
            log.warn("quiz_command_rejected action=start code={}", externalCode(ex.getCode()));
            throw ex;
        }
    }
    /** Принимает первый ответ/skip; одинаковый canonical retry допустим и после закрытия окна, другой конфликтует. */
    public QuizGameRoomResponse answer(String code, String email, QuizGameRoomRequest.Answer request) {
        return command(code, email, (room, student) -> {
            var participant = member(room, student);
            if (request == null || request.roundId() == null || (request.skipped() && request.answer() != null && !request.answer().isNull())) throw new QuizEngineException("ANSWER_INVALID");
            var round = rounds.findByIdAndRoomId(request.roundId(), room.getId()).orElseThrow(() -> new QuizEngineException("ROUND_NOT_ACTIVE"));
            var existing = answers.findByRoundIdAndParticipantId(round.getId(), participant.getId());
            if (existing.isPresent()) {
                var saved = existing.get();
                if (!saved.isTimedOut() && saved.isSkipped() == request.skipped()
                        && (request.skipped() || saved.getAnswerJson().equals(json.write(checker.canonical(round, request.answer()))))) {
                    advance(room, now()); return response(room, student);
                }
                throw new QuizEngineException("ANSWER_ALREADY_SUBMITTED");
            }
            // Время берётся после получения lock: ожидавший запрос не получает задним числом окно.
            if (room.getStatus() != QuizRoomStatus.ACTIVE) throw new QuizEngineException("ROUND_NOT_ACTIVE");
            answers.saveAndFlush(evaluator.evaluate(round, participant, request.answer(), request.skipped(), now()));
            events.publish("room.answer_accepted", room); return response(room, student);
        });
    }
    /** Удаляет только гостевое WAITING-участие; доставка STOMP проверяет актуальное членство после commit. */
    public void leave(String code, String email) {
        subscriptions.leave(code, email, () -> command(code, email, (room, student) -> {
            requireWaiting(room); if (isHost(room, student)) throw new QuizEngineException("GUEST_ONLY_ACTION");
            participants.findByRoomIdAndStudentProfileId(room.getId(), student.getId()).ifPresent(p -> {
                participants.delete(p); participants.flush(); events.publish("room.participant_left", room);
            }); return null;
        }));
    }
    /** Отменяет WAITING только организатором; повтор CANCELLED идемпотентен, история остаётся. */
    public QuizGameRoomResponse cancel(String code, String email) {
        return command(code, email, (room, student) -> {
            requireHost(room, student);
            if (room.getStatus() != QuizRoomStatus.CANCELLED) { requireWaiting(room); room.cancel(now()); events.publish("room.cancelled", room); }
            return response(room, student);
        });
    }
    /** Догоняет расписание и выдаёт только собственный разбор FINISHED-комнаты. */
    public QuizGameResultService.Result results(String code, String email) {
        return transact(() -> { var room = find(code, true); var student = student(email); member(room, student); if (room.isPlayable()) advance(room, now()); return results.read(room.getCode(), student.getId()); });
    }
    /** Одна транзакция на комнату: повреждённая игра не откатывает обработку остальных; flag каталога не участвует. */
    public void enforceTimeouts() {
        var codes = transact(() -> {
            var list = new ArrayList<String>();
            rooms.findExpiredWaiting(now(), PageRequest.of(0, 100)).stream().filter(QuizRoom::isPlayable).forEach(r -> list.add(r.getCode()));
            rooms.findByStatus(QuizRoomStatus.ACTIVE).stream().filter(QuizRoom::isPlayable).forEach(r -> list.add(r.getCode()));
            return list;
        });
        for (String code : codes) {
            try { tick(code); }
            catch (QuizEngineException ex) { log.warn("quiz_scheduler_rejected room={} code={}", code, externalCode(ex.getCode())); }
        }
    }
    /** Восстанавливает тайм-ауты/finish по сохранённому расписанию; повтор безопасен. */
    public void tick(String code) { transact(() -> { var room = find(code, true); requireGame(room); advance(room, now()); return null; }); }

    private <T> T command(String code, String email, java.util.function.BiFunction<QuizRoom, StudentProfile, T> action) {
        return transact(() -> { var room = find(code, true); requireGame(room); return action.apply(room, student(email)); });
    }
    private <T> T transact(Supplier<T> action) {
        for (int attempt = 0; ; attempt++) {
            try { return transaction.execute(status -> action.get()); }
            catch (PessimisticLockingFailureException | OptimisticLockingFailureException ex) {
                // REPEATABLE_READ может обнаружить commit конкурента после ожидания lock (SQLSTATE 40001).
                if (attempt >= 4) throw new QuizEngineException("ROOM_BUSY");
            }
            catch (ContentValidationException ex) { throw new QuizEngineException("GAME_CONFIGURATION_INVALID"); }
        }
    }
    private void advance(QuizRoom room, OffsetDateTime at) {
        if (room.getStatus() == QuizRoomStatus.WAITING) {
            room.expire(at); if (room.getStatus() == QuizRoomStatus.EXPIRED) events.publish("room.expired", room);
            return;
        }
        if (room.getStatus() != QuizRoomStatus.ACTIVE) return;
        var members = members(room); var snapshots = rounds.findByRoomIdOrderByOrderNoAsc(room.getId());
        int timedOut = 0;
        for (var round : snapshots) if (!at.isBefore(QuizAnswerEvaluator.endsAt(round))) {
            for (var participant : members) if (answers.findByRoundIdAndParticipantId(round.getId(), participant.getId()).isEmpty()) {
                answers.save(evaluator.timeout(round, participant, at));
                timedOut++;
            }
        }
        answers.flush();
        // Один безопасный сигнал на commit пачки timeout; повтор tick с сохранёнными ответами его не дублирует.
        if (timedOut > 0) events.publish("room.answers_timed_out", room);
        // Ранние ответы всех игроков не открывают будущие условия и не сокращают общее расписание.
        if (!at.isBefore(room.getStartedAt().plusSeconds((long) room.getQuestionCount() * room.getRoundSeconds()))) {
            room.finish(at); results.save(room.getCode()); events.publish("room.finished", room);
        } else if (!at.isBefore(room.getStartedAt())) {
            int currentOrder = (int) (Duration.between(room.getStartedAt(), at).getSeconds() / room.getRoundSeconds()) + 1;
            events.roundChanged(room, currentOrder);
        }
    }
    private String joinBlocker(QuizRoom room, StudentProfile student, List<QuizParticipant> members) {
        try {
            requireWaiting(room); requireSource(room);
            if (members.stream().noneMatch(p -> p.getStudentProfile().getId().equals(student.getId()))) {
                if (members.size() >= room.getMaxPlayers()) return "ROOM_FULL";
                var prospective = new ArrayList<>(members); prospective.add(new QuizParticipant(room, student, student.getUser().getFullName(), false));
                validateContent(room, prospective);
            }
            return null;
        } catch (QuizEngineException ex) { return externalCode(ex.getCode()); }
    }
    private void validateContent(QuizRoom room, List<QuizParticipant> members) {
        requireSource(room); var required = audience.resolve(room, members); var provider = providers.get(room.getGameType());
        if (provider == null) throw new QuizEngineException("GAME_CONFIGURATION_INVALID");
        int count = provider.prepare(room, required.gradeIds()).size();
        if (count < room.getQuestionCount()) throw new QuizEngineException("INSUFFICIENT_PUBLISHED_CONTENT", count, room.getQuestionCount());
    }
    private void validateSettings(QuizRoom room) {
        if (room.getKind() == QuizRoomKind.CUSTOM_SUBJECT) { validateCustomSettings(room); return; }
        var v = room.getGameVersion();
        if (room.getQuestionCount() < v.getMinQuestionCount() || room.getQuestionCount() > v.getMaxQuestionCount()
                || (!v.isQuestionCountEditable() && room.getQuestionCount() != v.getDefaultQuestionCount())
                || room.getRoundSeconds() < v.getMinRoundSeconds() || room.getRoundSeconds() > v.getMaxRoundSeconds()
                || (!v.isRoundSecondsEditable() && room.getRoundSeconds() != v.getDefaultRoundSeconds())) throw new QuizEngineException("SETTINGS_INVALID");
    }
    private Integer selectedGrade(QuizGameVersion version, StudentProfile host, Long gradeId) {
        if (version.getGradeSelectionMode() == GradeSelectionMode.NONE) {
            if (gradeId != null) throw new QuizEngineException("INVALID_GRADE"); return null;
        }
        Grade selected = gradeId == null ? null : grades.findById(gradeId).orElse(null);
        if (selected == null && gradeId == null && version.getGradeSelectionMode() == GradeSelectionMode.PROFILE_DEFAULT)
            selected = grades.findAllByOrderByGradeNoAsc().stream().filter(g -> Objects.equals(g.getGradeNo(), host.getGradeNo())).findFirst().orElse(null);
        if (selected == null || !version.getGradeIds().contains(selected.getId())) throw new QuizEngineException("INVALID_GRADE");
        return selected.getGradeNo();
    }
    private QuizGameRoomResponse response(QuizRoom room, StudentProfile student) {
        var current = member(room, student); var members = members(room); var at = now(); var v = room.getGameVersion();
        var blockers = new ArrayList<String>();
        if (effectiveStatus(room) != QuizRoomStatus.WAITING) blockers.add(effectiveStatus(room) == QuizRoomStatus.EXPIRED ? "ROOM_EXPIRED" : "ROOM_ALREADY_STARTED");
        else {
            if (members.stream().anyMatch(p -> !p.isReady() || !Objects.equals(p.getReadyRevision(), room.getSettingsRevision()))) blockers.add("PARTICIPANT_NOT_READY");
            try { validateContent(room, members); } catch (QuizEngineException ex) { blockers.add(externalCode(ex.getCode())); }
        }
        var state = reader.read(room.getCode(), student.getId(), at); var currentRound = state.currentRound();
        List<QuizGameRoomResponse.Window> windows = new ArrayList<>();
        for (int i = 1; i <= room.getQuestionCount(); i++) {
            var start = room.getStartedAt() == null ? null : room.getStartedAt().plusSeconds((i - 1L) * room.getRoundSeconds());
            windows.add(new QuizGameRoomResponse.Window(i, start, start == null ? null : start.plusSeconds(room.getRoundSeconds())));
        }
        var selected = grades.findAllByOrderByGradeNoAsc().stream().filter(g -> Objects.equals(g.getGradeNo(), room.getAudienceGradeNo())).findFirst().orElse(null);
        boolean host = isHost(room, student);
        return new QuizGameRoomResponse(room.getId(), room.getCode(), room.getStatus().name().toLowerCase(Locale.ROOT), host, current.getId(),
                room.getSubject() == null ? null : room.getSubject().getId(), room.getSubject() == null ? null : QuizSnapshotJson.localized(room.getSubject().getTitleRu(), room.getSubject().getTitleKk(), room.getLanguage()),
                selected == null ? null : selected.getId(), selected == null ? null : QuizSnapshotJson.localized(selected.getTitleRu(), selected.getTitleKk(), room.getLanguage()),
                room.getLanguage(), room.getDifficulty(), room.getQuestionCount(), room.getRoundSeconds(), room.getMaxPlayers(), at,
                currentRound == null ? null : currentRound.id(), room.getCreatedAt(), room.getStartedAt(), room.getFinishedAt(),
                members.stream().map(p -> new QuizGameRoomResponse.Member(p.getId(), p.getDisplayName(), p.isReady(), isHost(room, p.getStudentProfile()), p.getId().equals(current.getId()))).toList(), List.copyOf(windows),
                room.getGame() == null ? null : room.getGame().getId(), room.getGame() == null ? null : room.getGame().getCode(), room.getGameType().name(), title(room),
                v == null ? "" : QuizSnapshotJson.localized(v.getDescriptionRu(), v.getDescriptionKk(), room.getLanguage()), host, host && blockers.isEmpty(), List.copyOf(blockers),
                effectiveStatus(room) == QuizRoomStatus.WAITING && members.size() < room.getMaxPlayers(), room.getExpiresAt(), room.getSettingsRevision(), currentRound,
                currentRound == null ? null : switch (room.getGameType()) { case SUBJECT_QUIZ -> "QUESTION"; case WORD_TRANSLATION -> "VOCABULARY"; case TRUE_FALSE -> "FACT"; },
                room.getAudienceGradeNo(), v == null ? "REQUIRED" : v.getGradeSelectionMode().name(), v == null ? "ROOM_GRADE" : v.getAudiencePolicy().name(), v == null || v.isQuestionCountEditable(), v == null || v.isRoundSecondsEditable(),
                v == null ? 1 : v.getMinQuestionCount(), v == null ? 50 : v.getMaxQuestionCount(), v == null ? 5 : v.getMinRoundSeconds(), v == null ? 120 : v.getMaxRoundSeconds());
    }
    private QuizRoom find(String code, boolean lock) {
        // Исторические коды сохраняют прежнее написание; неизвестный код проходит строгую проверку.
        String normalized = code != null && code.trim().length() <= 16 && rooms.existsByCodeIgnoreCase(code.trim()) ? code.trim() : QuizRoomCode.normalize(code);
        var room = (lock ? rooms.findCommandLockedByCode(normalized) : rooms.findByCodeIgnoreCase(normalized)).orElseThrow(() -> new QuizEngineException("ROOM_NOT_FOUND"));
        if (lock) {
            // Сначала обычный WAIT lock, затем FORCE_INCREMENT уже своей заблокированной строки.
            // Hibernate PostgreSQL FORCE_INCREMENT напрямую использует NOWAIT: пять мгновенных retry
            // могут исчерпаться до commit конкурента. Раздельные шаги сохраняют ожидание и @Version.
            entityManager.lock(room, jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }
        return room;
    }
    private void requireGame(QuizRoom room) { if (!room.isPlayable()) throw new QuizEngineException("RESULTS_NOT_AVAILABLE"); }
    private void requireSource(QuizRoom room) {
        if (room.getKind() == QuizRoomKind.CUSTOM_SUBJECT) return;
        try { availability.requireWaitingStart(room.getGameVersion()); }
        catch (ContentValidationException ex) { throw new QuizEngineException("GAME_CONFIGURATION_INVALID"); }
    }
    private StudentProfile student(String email) { return students.findByUserEmailIgnoreCase(email).orElseThrow(() -> new QuizEngineException("PROFILE_GRADE_REQUIRED")); }
    private QuizParticipant member(QuizRoom room, StudentProfile student) { return participants.findByRoomIdAndStudentProfileId(room.getId(), student.getId()).orElseThrow(() -> new QuizEngineException("PARTICIPANT_REQUIRED")); }
    private List<QuizParticipant> members(QuizRoom room) { return participants.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId()); }
    private boolean isHost(QuizRoom room, StudentProfile student) { return room.getHostStudentProfile().getId().equals(student.getId()); }
    private void requireHost(QuizRoom room, StudentProfile student) { member(room, student); if (!isHost(room, student)) throw new QuizEngineException("HOST_ONLY_ACTION"); }
    private QuizRoomStatus effectiveStatus(QuizRoom room) { return room.getStatus() == QuizRoomStatus.WAITING && room.getExpiresAt() != null && !now().isBefore(room.getExpiresAt()) ? QuizRoomStatus.EXPIRED : room.getStatus(); }
    private void requireWaiting(QuizRoom room) {
        switch (effectiveStatus(room)) {
            case WAITING -> { }
            case EXPIRED -> throw new QuizEngineException("ROOM_EXPIRED");
            case CANCELLED -> throw new QuizEngineException("ROOM_CANCELLED");
            default -> throw new QuizEngineException("ROOM_ALREADY_STARTED");
        }
    }
    private String title(QuizRoom room) {
        if (room.getGameVersion() == null) return room.getSubject() == null ? room.getCode()
                : QuizSnapshotJson.localized(room.getSubject().getTitleRu(), room.getSubject().getTitleKk(), room.getLanguage());
        String title = QuizSnapshotJson.localized(room.getGameVersion().getTitleRu(), room.getGameVersion().getTitleKk(), room.getLanguage());
        if (title.isBlank()) throw new QuizEngineException("GAME_CONFIGURATION_INVALID"); return title;
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    /** Переводит внутреннюю диагностику движка в согласованные безопасные API-коды. */
    public static String externalCode(String code) {
        return switch (code) {
            case "AUDIENCE_INVALID", "GRADE_REQUIRED" -> "INVALID_GRADE";
            case "ANSWER_REQUIRED" -> "ANSWER_INVALID";
            case "SNAPSHOT_INVALID", "DUPLICATE_SOURCE", "GAME_TYPE_UNSUPPORTED", "ROUND_SET_ALREADY_CREATED", "SOURCE_UNAVAILABLE", "SUBJECT_UNIVERSAL_UNSUPPORTED", "ANSWER_TIMING_INVALID", "DUPLICATE_ANSWER", "LEGACY_ROOM", "ROOM_NOT_STARTED", "ROUND_NOT_ENDED" -> "GAME_CONFIGURATION_INVALID";
            default -> code;
        };
    }
    private static final class CodeCollision extends RuntimeException {}
}
