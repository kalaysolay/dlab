package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import kz.damulab.quiz.QuizRoom;
import kz.damulab.users.StudentProfile;

/** Хеши ключа create и payload: одна запись на ученика/ключ. Raw token и ответы здесь не хранятся. */
@Entity @Table(name = "quiz_room_creation_keys")
public class QuizRoomCreationKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_profile_id") private StudentProfile studentProfile;
    @Column(name = "key_hash", length = 64, nullable = false) private String keyHash;
    @Column(name = "payload_hash", length = 64, nullable = false) private String payloadHash;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id") private QuizRoom room;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    protected QuizRoomCreationKey() {}
    /** Создаёт соответствие после успешного create в той же транзакции. TTL-удаления нет. */
    public QuizRoomCreationKey(StudentProfile studentProfile, String keyHash, String payloadHash, QuizRoom room) {
        this.studentProfile = studentProfile; this.keyHash = keyHash; this.payloadHash = payloadHash; this.room = room;
    }
    /** Возвращает ID записи. */
    public Long getId() { return id; }
    /** Возвращает владельца ключа. */
    public StudentProfile getStudentProfile() { return studentProfile; }
    /** Возвращает хеш ключа, не исходный токен. */
    public String getKeyHash() { return keyHash; }
    /** Возвращает хеш для обнаружения повторов с другим запросом. */
    public String getPayloadHash() { return payloadHash; }
    /** Возвращает ранее созданную комнату. */
    public QuizRoom getRoom() { return room; }
    /** Возвращает время создания без автоматического срока удаления. */
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
