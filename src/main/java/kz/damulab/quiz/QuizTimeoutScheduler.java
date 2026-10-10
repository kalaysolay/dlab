package kz.damulab.quiz;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Обслуживает оба режима независимо от флага каталога и открытых вкладок. */
@Component
@ConditionalOnProperty(prefix = "damulab.quiz", name = "timeout-worker-enabled", havingValue = "true", matchIfMissing = true)
public class QuizTimeoutScheduler {

    private final QuizService quizService;
    private final QuizRoomService roomService;

    /** Подключает legacy-таймер и новую оркестрацию без изменения прежней семантики. */
    public QuizTimeoutScheduler(QuizService quizService, QuizRoomService roomService) {
        this.quizService = quizService;
        this.roomService = roomService;
    }

    /** Догоняет сохранённое серверное расписание после рестарта и истекающее WAITING. */
    @Scheduled(
            fixedDelayString = "${damulab.quiz.timeout-worker-delay-ms:1000}",
            initialDelayString = "${damulab.quiz.timeout-worker-initial-delay-ms:${damulab.quiz.timeout-worker-delay-ms:1000}}"
    )
    public void enforceTimeouts() {
        quizService.enforceActiveRoomTimeouts();
        roomService.enforceTimeouts();
    }
}
