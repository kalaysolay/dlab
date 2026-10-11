package kz.damulab.quiz;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Единственный таймер предметных и каталожных игр, не зависит от открытых вкладок. */
@Component
@ConditionalOnProperty(prefix = "damulab.quiz", name = "timeout-worker-enabled", havingValue = "true", matchIfMissing = true)
public class QuizTimeoutScheduler {

    private final QuizRoomService roomService;

    /** Подключает общую оркестрацию комнат. */
    public QuizTimeoutScheduler(QuizRoomService roomService) {
        this.roomService = roomService;
    }

    /** Догоняет сохранённое серверное расписание после рестарта и истекающее WAITING. */
    @Scheduled(
            fixedDelayString = "${damulab.quiz.timeout-worker-delay-ms:1000}",
            initialDelayString = "${damulab.quiz.timeout-worker-initial-delay-ms:${damulab.quiz.timeout-worker-delay-ms:1000}}"
    )
    public void enforceTimeouts() {
        roomService.enforceTimeouts();
    }
}
