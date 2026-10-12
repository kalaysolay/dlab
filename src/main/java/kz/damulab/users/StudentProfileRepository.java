package kz.damulab.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Поиск профилей и блокировка владельца idempotency key при создании игровых комнат. */
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    /** Сериализует create одного ученика, в том числе разные вкладки с одним idempotency key. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from StudentProfile s where lower(s.user.email) = lower(:email)")
    Optional<StudentProfile> findLockedByEmail(@org.springframework.data.repository.query.Param("email") String email);

    Optional<StudentProfile> findByUserEmailIgnoreCase(String email);

    Optional<StudentProfile> findByUserId(Long userId);
}
