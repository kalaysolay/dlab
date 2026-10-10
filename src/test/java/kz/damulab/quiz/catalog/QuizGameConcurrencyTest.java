package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Проверяет конкурирующие редакционные транзакции на H2 и изолированном PostgreSQL без браузерных таймеров. */
class QuizGameConcurrencyTest extends QuizGameTestSupport {
    @Autowired PlatformTransactionManager transactions;

    @Test
    void editAndPublishCannotReleaseUnreviewedFields() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        Long id = tx.execute(s -> createApproved(factForm(1)).getId());
        QuizGameForm oldForm = tx.execute(s -> games.get(id).form()); long oldRevision = oldForm.getExpectedRevision();
        CountDownLatch saved = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        AtomicReference<Throwable> publishError = new AtomicReference<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> editing = executor.submit(() -> tx.executeWithoutResult(s -> {
                oldForm.setTitleRu("Неутверждённые поля"); games.saveDraft(id, oldForm, admin()); saved.countDown(); await(release);
            }));
            assertThat(saved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> publishing = executor.submit(() -> { started.countDown(); try { games.publish(id, oldRevision, true, admin()); } catch (Throwable ex) { publishError.set(ex); } });
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue(); release.countDown();
            editing.get(15, TimeUnit.SECONDS); publishing.get(15, TimeUnit.SECONDS);
            assertThat(publishError.get()).isNotNull();
            tx.executeWithoutResult(s -> {
                QuizGame game = gameRepository.findById(id).orElseThrow(); assertThat(game.getPublishedVersion()).isNull();
                assertThat(game.getDraftVersion().getStatus()).isEqualTo(EditorialStatus.DRAFT);
                assertThat(game.getDraftVersion().getReviewedAt()).isNull();
            });
        } finally {
            release.countDown(); tx.executeWithoutResult(s -> { QuizGame game = gameRepository.findById(id).orElseThrow();
                games.archive(id, game.getAdminRevision(), admin()); });
        }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Тестовая транзакция не получила сигнал"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }
}
