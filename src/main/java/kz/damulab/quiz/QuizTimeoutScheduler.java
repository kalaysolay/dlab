package kz.damulab.quiz;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "damulab.quiz", name = "timeout-worker-enabled", havingValue = "true", matchIfMissing = true)
public class QuizTimeoutScheduler {

    private final QuizService quizService;

    public QuizTimeoutScheduler(QuizService quizService) {
        this.quizService = quizService;
    }

    @Scheduled(
            fixedDelayString = "${damulab.quiz.timeout-worker-delay-ms:1000}",
            initialDelayString = "${damulab.quiz.timeout-worker-initial-delay-ms:${damulab.quiz.timeout-worker-delay-ms:1000}}"
    )
    public void enforceTimeouts() {
        quizService.enforceActiveRoomTimeouts();
    }
}
