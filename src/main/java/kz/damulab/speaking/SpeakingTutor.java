package kz.damulab.speaking;

import java.util.List;

/** Отдельный от генератора лекций контракт собеседника. В первом срезе допускается только локальный stub. */
interface SpeakingTutor {
    Reply answer(SpeakingScenario scenario, int acceptedTurns, String text, String language);
    record Feedback(String skillCode, String original, String corrected, String explanation) {}
    record Reply(String reaction, String question, List<Feedback> feedback) {}
}
