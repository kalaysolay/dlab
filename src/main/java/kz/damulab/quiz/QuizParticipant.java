package kz.damulab.quiz;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import kz.damulab.users.StudentProfile;

@Entity
@Table(name = "quiz_participants")
public class QuizParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private QuizRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean ready;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    protected QuizParticipant() {
    }

    public QuizParticipant(QuizRoom room, StudentProfile studentProfile, String displayName, boolean ready) {
        this.room = room;
        this.studentProfile = studentProfile;
        this.displayName = displayName;
        this.ready = ready;
    }

    public void markReady() {
        ready = true;
    }

    public Long getId() {
        return id;
    }

    public QuizRoom getRoom() {
        return room;
    }

    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isReady() {
        return ready;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }
}
