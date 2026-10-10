package kz.damulab.quiz;

/** Состояния комнаты; отмена и истечение не удаляют её историю. */
public enum QuizRoomStatus {
    WAITING,
    ACTIVE,
    FINISHED,
    CANCELLED,
    EXPIRED
}
