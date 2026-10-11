package kz.damulab.quiz;

import kz.damulab.quiz.catalog.*;
import jakarta.persistence.*;
import java.util.*;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import kz.damulab.content.Grade;
import kz.damulab.content.Subject;
import kz.damulab.users.StudentProfile;

@Entity
@Table(name = "quiz_rooms")
/** Компонент комнаты: nullable-поля сохраняют legacy, новые механики опираются на версию и снимки. */
public class QuizRoom {

    @Enumerated(EnumType.STRING) @Column(name = "room_kind", nullable = false, length = 32)
    private QuizRoomKind kind = QuizRoomKind.HISTORY;

    /** Возвращает источник комнаты независимо от наличия ссылки на каталог. */
    public QuizRoomKind getKind() { return kind; }

    /** Указывает, что команда обслуживается единственным действующим движком. */
    public boolean isPlayable() { return kind != QuizRoomKind.HISTORY; }

    /** Создаёт самостоятельную предметную комнату без скрытой игры в каталоге. */
    public void initializeCustom(OffsetDateTime expiry) {
        if (id != null || gameVersion != null || subject == null || grade == null)
            throw new IllegalStateException("Некорректная предметная комната");
        kind = QuizRoomKind.CUSTOM_SUBJECT;
        audienceGradeNo = grade.getGradeNo();
        expiresAt = expiry;
    }

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "game_id")
    private QuizGame game;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "game_version_id")
    private QuizGameVersion gameVersion;

    @Enumerated(EnumType.STRING) @Column(name = "game_type", length = 32)
    private QuizGameType gameType;

    @Column(name = "audience_grade_no")
    private Integer audienceGradeNo;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "settings_revision")
    private long settingsRevision;

    @Version
    @Column(name = "command_revision", nullable = false)
    private long commandRevision;

    @ElementCollection @CollectionTable(name = "quiz_room_audience_grades", joinColumns = @JoinColumn(name = "room_id")) @Column(name = "grade_no")
    private Set<Integer> audienceGrades = new LinkedHashSet<>();


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_student_profile_id", nullable = false)
    private StudentProfile hostStudentProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @Column(nullable = false, length = 8)
    private String language;

    @Column
    private Integer difficulty;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "round_seconds", nullable = false)
    private int roundSeconds;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuizRoomStatus status = QuizRoomStatus.WAITING;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    /** Возвращает game. */
    public QuizGame getGame() { return game; }

    /** Возвращает gameVersion. */
    public QuizGameVersion getGameVersion() { return gameVersion; }

    /** Возвращает gameType. */
    public QuizGameType getGameType() { return kind == QuizRoomKind.CATALOG_GAME ? gameType : QuizGameType.SUBJECT_QUIZ; }

    /** Возвращает audienceGradeNo. */
    public Integer getAudienceGradeNo() { return audienceGradeNo; }

    /** Возвращает expiresAt. */
    public OffsetDateTime getExpiresAt() { return expiresAt; }

    /** Возвращает cancelledAt. */
    public OffsetDateTime getCancelledAt() { return cancelledAt; }

    /** Возвращает settingsRevision. */
    public long getSettingsRevision() { return settingsRevision; }

    /** Возвращает audienceGrades. */
    public Set<Integer> getAudienceGrades() { return Collections.unmodifiableSet(audienceGrades); }

    /** Фиксирует фактическую аудиторию одновременно с полным набором снимков, не меняя revision настроек. */
    public void recordAudience(Set<Integer> grades) {
        if (status != QuizRoomStatus.WAITING) throw new IllegalStateException("Аудитория уже зафиксирована");
        audienceGrades = new LinkedHashSet<>(grades);
    }

    /** Фиксирует версию при create; доступность проверяет командный сервис следующего этапа. */
    public void attachGameVersion(QuizGameVersion version, OffsetDateTime expiry) {
        if (status != QuizRoomStatus.WAITING || gameVersion != null) throw new IllegalStateException("Версия уже зафиксирована");
        kind = QuizRoomKind.CATALOG_GAME;
        game = version.getGame(); gameVersion = version; gameType = version.getGameType(); expiresAt = expiry;
    }
    /** Задаёт исходный выбранный класс до первого INSERT; первая редакция настроек имеет revision 0. */
    public void initializeAudienceGrade(Integer gradeNo) {
        if (id != null || gameVersion == null || status != QuizRoomStatus.WAITING) throw new IllegalStateException("Исходные настройки уже сохранены");
        audienceGradeNo = gradeNo;
    }
    /** Записывает настройки WAITING; сервис в той же транзакции сбрасывает готовность гостей. */
    public void changeSettings(Integer audienceGradeNo, Set<Integer> audienceGrades, int questionCount, int roundSeconds) {
        if (status != QuizRoomStatus.WAITING) throw new IllegalStateException("Настройки доступны только в ожидании");
        this.audienceGradeNo = audienceGradeNo; this.audienceGrades = new LinkedHashSet<>(audienceGrades);
        this.questionCount = questionCount; this.roundSeconds = roundSeconds; settingsRevision++;
    }
    /** Синхронизирует исходный FK класса с настройками самостоятельной игры в той же транзакции. */
    public void changeCustomGrade(Grade selected) {
        if (kind != QuizRoomKind.CUSTOM_SUBJECT || status != QuizRoomStatus.WAITING || selected == null)
            throw new IllegalStateException("Класс нельзя менять");
        grade = selected;
    }
    /** Отменяет WAITING, не удаляя историю. */
    public void cancel(OffsetDateTime now) {
        if (status != QuizRoomStatus.WAITING) throw new IllegalStateException("Отмена доступна только в ожидании");
        status = QuizRoomStatus.CANCELLED; cancelledAt = now;
    }
    /** Помечает истёкшую WAITING; legacy без срока и активные комнаты не затрагиваются. */
    public void expire(OffsetDateTime now) {
        if (status == QuizRoomStatus.WAITING && expiresAt != null && !now.isBefore(expiresAt)) status = QuizRoomStatus.EXPIRED;
    }

    protected QuizRoom() {
    }

    /** Создаёт настройки ещё не сохранённой комнаты; версия/аудитория и права проверяются командным сервисом. */
    public QuizRoom(
            String code,
            StudentProfile hostStudentProfile,
            Subject subject,
            Grade grade,
            String language,
            Integer difficulty,
            int questionCount,
            int roundSeconds,
            int maxPlayers
    ) {
        this.code = code;
        this.hostStudentProfile = hostStudentProfile;
        this.subject = subject;
        this.grade = grade;
        this.language = language;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
        this.roundSeconds = roundSeconds;
        this.maxPlayers = maxPlayers;
    }

    /** Запускает общее расписание; сервис вызывает только после атомарного сохранения всего набора раундов. */
    public void start(OffsetDateTime now) {
        if (status != QuizRoomStatus.WAITING) {
            return;
        }
        status = QuizRoomStatus.ACTIVE;
        startedAt = now;
    }

    /** Завершает игру один раз; сохранение результатов выполняется сервисом в той же транзакции. */
    public void finish(OffsetDateTime now) {
        // Новые терминальные состояния нельзя превратить в результаты игры.
        if (status == QuizRoomStatus.CANCELLED || status == QuizRoomStatus.EXPIRED)
            throw new IllegalStateException("Отменённая или истёкшая комната не завершается повторно");
        if (status == QuizRoomStatus.FINISHED) {
            return;
        }
        status = QuizRoomStatus.FINISHED;
        finishedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public StudentProfile getHostStudentProfile() {
        return hostStudentProfile;
    }

    public Subject getSubject() {
        return subject;
    }

    public Grade getGrade() {
        return grade;
    }

    public String getLanguage() {
        return language;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getRoundSeconds() {
        return roundSeconds;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public QuizRoomStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }
}
