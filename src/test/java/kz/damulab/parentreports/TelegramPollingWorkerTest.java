package kz.damulab.parentreports;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Проверяет режим, подтверждение offset и повтор после ошибки без сети и настоящих чатов. */
class TelegramPollingWorkerTest {
    final ObjectMapper mapper = new ObjectMapper();
    final TelegramClient telegram = mock(TelegramClient.class);
    ParentTelegramService service(String mode) {
        var config = new TelegramProperties(true,"test","test_bot","",mode,"http://localhost:8080");
        return spy(new ParentTelegramService(mock(JdbcTemplate.class),mock(TransactionTemplate.class),
                Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC),config,telegram));
    }
    @Test void pollingDoesNotRequireWebhookSecret() {
        assertThat(service("polling").config.available()).isTrue();
        assertThat(service("webhook").config.available()).isFalse();
        assertThat(service("typo").config.available()).isFalse();
    }
    @Test void acknowledgesOnlyHandledUpdates() throws Exception {
        var service = service("polling"); doNothing().when(service).update(any());
        var updates = mapper.readTree("[{\"update_id\":123},{\"update_id\":124}]");
        when(telegram.updates(0)).thenReturn(updates);
        when(telegram.updates(125)).thenReturn(mapper.createArrayNode());
        var worker = new TelegramPollingWorker(service); worker.poll(); worker.poll();
        verify(service).update(updates.get(0)); verify(service).update(updates.get(1));
        verify(telegram).updates(125);
    }
    @Test void failureDoesNotAcknowledgeFailedUpdate() throws Exception {
        var service = service("polling");
        when(telegram.updates(0)).thenReturn(mapper.readTree("[{\"update_id\":123}]"));
        doThrow(new IllegalStateException()).when(service).update(any());
        var worker = new TelegramPollingWorker(service); worker.poll(); worker.poll();
        verify(telegram,times(1)).updates(0); verify(telegram,never()).updates(124);
        // Новый процесс повторит ещё не подтверждённое событие, а update_id в БД не даст
        // повторно выполнить уже успешно сохранённое действие.
        new TelegramPollingWorker(service).poll(); verify(telegram,times(2)).updates(0);
    }
    @Test void webhookModeDoesNotPoll() {
        new TelegramPollingWorker(service("webhook")).poll(); verifyNoInteractions(telegram);
    }
}
