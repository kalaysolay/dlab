package kz.damulab.speaking;

import java.util.List;

/** Отдельный от лекций контракт учебного ответа. LiveSpeakingTutor добавляет контекст и независимые этапы проверки. */
interface SpeakingTutor {
    Reply answer(SpeakingScenario scenario, int acceptedTurns, String text, String language);
    record Feedback(String skillCode, String original, String corrected, String explanation,String kind) {
        Feedback(String skillCode,String original,String corrected,String explanation){this(skillCode,original,corrected,explanation,"error");}
    }
    record Reply(String reaction, String question, List<Feedback> feedback,String disposition) {
        Reply(String reaction,String question,List<Feedback> feedback){this(reaction,question,feedback,"NORMAL");}
    }
}
