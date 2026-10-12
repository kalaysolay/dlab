package kz.damulab.quiz;

/** История прежних игр читается без пересчёта; остальные комнаты имеют общий жизненный цикл. */
public enum QuizRoomKind {
    HISTORY, CATALOG_GAME, CUSTOM_SUBJECT
}
