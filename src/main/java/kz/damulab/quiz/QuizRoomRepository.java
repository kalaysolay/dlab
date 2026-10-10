package kz.damulab.quiz;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;

/** Поиск комнат; lock требует внешнюю транзакцию командного сервиса. */
public interface QuizRoomRepository extends JpaRepository<QuizRoom, Long> {
    /** Ожидает room lock; сервис увеличивает техническую версию только после получения блокировки. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from QuizRoom r where lower(r.code) = lower(:code)")
    Optional<QuizRoom> findCommandLockedByCode(@Param("code") String code);

    /** Блокирует одну комнату для атомарных команд settings/start/answer. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from QuizRoom r where lower(r.code) = lower(:code)")
    Optional<QuizRoom> findLockedByCode(@Param("code") String code);

    /** Выбирает пачку истёкших WAITING; null expiry legacy не считается истечением. */
    @Query("select r from QuizRoom r where r.status = kz.damulab.quiz.QuizRoomStatus.WAITING and r.expiresAt <= :now order by r.expiresAt, r.id")
    java.util.List<QuizRoom> findExpiredWaiting(@Param("now") OffsetDateTime now, Pageable page);

    /** Совпадает с выражением V55; прежние mixed-case коды продолжают читаться. */
    @Query("select r from QuizRoom r where lower(r.code) = lower(:code)")
    Optional<QuizRoom> findByCodeIgnoreCase(@Param("code") String code);

    java.util.List<QuizRoom> findByStatus(QuizRoomStatus status);

    /** Проверка коллизии использует тот же индекс, включая ещё не занятые коды. */
    @Query("select count(r) > 0 from QuizRoom r where lower(r.code) = lower(:code)")
    boolean existsByCodeIgnoreCase(@Param("code") String code);
}
