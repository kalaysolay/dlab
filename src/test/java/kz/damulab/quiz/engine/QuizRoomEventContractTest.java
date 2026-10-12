package kz.damulab.quiz.engine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.*;
import kz.damulab.quiz.*;

/** Контракт after-commit событий: rollback не сообщает о несуществующем состоянии. */
class QuizRoomEventContractTest {
    @Test void capturesSafeMetadataAndSendsOnlyAfterCommit() {
        var transport = mock(SimpMessagingTemplate.class); var clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
        var publisher = new QuizRoomEventPublisher(transport, clock); var room = new QuizRoom("QZABCD", null, null, null, "ru", null, 1, 20, 4);
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish("room.started", room); verifyNoInteractions(transport);
            var synchronizations = TransactionSynchronizationManager.getSynchronizations(); assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(TransactionSynchronization::afterCommit);
            verify(transport).convertAndSend(eq("/topic/quiz.rooms.QZABCD"), eq(new QuizRoomEvent("room.started", "QZABCD", "waiting", OffsetDateTime.now(clock))));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void rolledBackCommandDoesNotSendEvent() {
        var transport = mock(SimpMessagingTemplate.class); var publisher = new QuizRoomEventPublisher(transport, Clock.systemUTC());
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish("room.cancelled", new QuizRoom("QZABCD", null, null, null, "ru", null, 1, 20, 4));
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(transport);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void roundSignalAdvancesEvenWithoutTimeoutAndSuppressesRepeatedTick() {
        var transport = mock(SimpMessagingTemplate.class); var clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
        var publisher = new QuizRoomEventPublisher(transport, clock); var room = new QuizRoom("QZABCD", null, null, null, "ru", null, 3, 20, 4);
        room.start(OffsetDateTime.now(clock)); publisher.publish("room.started", room); clearInvocations(transport);
        publisher.roundChanged(room, 1); verifyNoInteractions(transport);
        publisher.roundChanged(room, 2); publisher.roundChanged(room, 2);
        verify(transport, times(1)).convertAndSend(eq("/topic/quiz.rooms.QZABCD"), eq(new QuizRoomEvent("room.round_changed", "QZABCD", "active", OffsetDateTime.now(clock))));
    }
    @Test void codesUseHistoricalAlphabetLocaleIndependentCaseAndNoInnerRepair() {
        assertThat(QuizRoomCode.normalize(" qz7k2m ")).isEqualTo("QZ7K2M");
        for (String code : java.util.List.of("QZ7 K2M", "QZ0000", "QZабвг", "QZABCD*", "QZABCD/", "QZABCDE", ""))
            assertThatThrownBy(() -> QuizRoomCode.normalize(code)).isInstanceOf(QuizEngineException.class).hasMessage("ROOM_CODE_INVALID");
    }
    @Test void structuredLogContainsOnlyCommittedSafeMetadata() {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(QuizRoomEventPublisher.class);
        var captured=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        captured.start(); logger.addAppender(captured);
        var transport=mock(SimpMessagingTemplate.class);
        var publisher=new QuizRoomEventPublisher(transport,Clock.systemUTC());
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish("room.answers_timed_out",new QuizRoom("QZABCD",null,null,null,"ru",null,1,20,4));
            // Ни сообщение в лог, ни транспорт не подтверждают транзакцию раньше самой БД.
            assertThat(captured.list).isEmpty();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertThat(captured.list).extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .containsExactly("quiz_event type=room.answers_timed_out room=QZABCD status=waiting");
            captured.list.clear(); TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.initSynchronization();
            publisher.publish("room.cancelled",new QuizRoom("QZABCD",null,null,null,"ru",null,1,20,4));
            TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(captured.list).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization(); logger.detachAppender(captured); captured.stop();
        }
    }
    @Test void repeatedStartIsLoggedAfterCommitWithoutExtraTransportSignal() {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(QuizRoomEventPublisher.class);
        var captured=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        captured.start(); logger.addAppender(captured);
        var transport=mock(SimpMessagingTemplate.class); var publisher=new QuizRoomEventPublisher(transport,Clock.systemUTC());
        var room=new QuizRoom("QZABCD",null,null,null,"ru",null,1,20,4); room.start(OffsetDateTime.now());
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.repeatedStart(room); assertThat(captured.list).isEmpty();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertThat(captured.list).extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .containsExactly("quiz_command_repeated action=start room=QZABCD status=active");
            verifyNoInteractions(transport);
        } finally {
            TransactionSynchronizationManager.clearSynchronization(); logger.detachAppender(captured); captured.stop();
        }
    }
}
