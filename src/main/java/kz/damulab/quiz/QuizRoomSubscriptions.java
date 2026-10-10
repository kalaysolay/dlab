package kz.damulab.quiz;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;
import java.security.Principal;
import kz.damulab.users.StudentProfileRepository;
import org.springframework.messaging.*;
import org.springframework.messaging.support.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Авторизация точных STOMP-подписок и каждой фактической доставки. Read-lock держится
 * до завершения outbound handler, write-lock leave охватывает commit и отзыв всех вкладок.
 * Таким образом queued-событие не проходит по разрешению, выданному до выхода.
 */
@Component
public class QuizRoomSubscriptions implements ExecutorChannelInterceptor {
    private final QuizRoomRepository rooms;
    private final QuizParticipantRepository participants;
    private final StudentProfileRepository students;
    private final TransactionTemplate transaction;
    private final ConcurrentMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock[] locks = new ReentrantReadWriteLock[64];
    private final ThreadLocal<Deque<Lock>> held = ThreadLocal.withInitial(ArrayDeque::new);
    /** Реестр принадлежит текущему серверу, reconnect всегда заново проверяет БД. */
    public QuizRoomSubscriptions(QuizRoomRepository rooms, QuizParticipantRepository participants,
            StudentProfileRepository students, PlatformTransactionManager manager) {
        this.rooms = rooms; this.participants = participants; this.students = students;
        transaction = new TransactionTemplate(manager); transaction.setReadOnly(true);
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantReadWriteLock(true);
    }
    /** Запрещает SEND/wildcard и подписку без STUDENT/membership; UNSUBSCRIBE/DISCONNECT чистят реестр. */
    public Message<?> inbound(Message<?> message) {
        var header = StompHeaderAccessor.wrap(message); var command = header.getCommand();
        if (command == StompCommand.SEND) throw new AccessDeniedException("Командный транспорт — HTTP");
        if (command == StompCommand.CONNECT || command == StompCommand.SUBSCRIBE) requireStudent(header.getUser());
        if (command == StompCommand.DISCONNECT) removeSession(header.getSessionId());
        if (command == StompCommand.UNSUBSCRIBE) subscriptions.remove(key(header.getSessionId(), header.getSubscriptionId()));
        if (command != StompCommand.SUBSCRIBE) return message;
        String destination = header.getDestination();
        if (destination == null || !destination.startsWith("/topic/quiz.rooms.")) throw new AccessDeniedException("Назначение недоступно");
        String code;
        try { code = QuizRoomCode.normalize(destination.substring("/topic/quiz.rooms.".length())); }
        catch (RuntimeException ex) { throw new AccessDeniedException("Недопустимый код"); }
        if (!destination.equals("/topic/quiz.rooms." + code) || header.getSessionId() == null || header.getSubscriptionId() == null)
            throw new AccessDeniedException("Нужна точная подписка");
        Lock lock = lock(code).readLock(); lock.lock();
        try {
            String email = header.getUser().getName();
            if (!member(code, email)) throw new AccessDeniedException("Нет участия в комнате");
            subscriptions.put(key(header.getSessionId(), header.getSubscriptionId()), new Subscription(code, email, header.getUser()));
        } finally { lock.unlock(); }
        return message;
    }
    /** Проверяет queued-доставку непосредственно перед handler, удерживая запрет конкурентного leave. */
    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        var header = SimpMessageHeaderAccessor.wrap(message);
        if (header.getMessageType() != SimpMessageType.MESSAGE) return message;
        String key = key(header.getSessionId(), header.getSubscriptionId()); var subscription = subscriptions.get(key);
        if (subscription == null || !Objects.equals(header.getDestination(), "/topic/quiz.rooms." + subscription.code())) return null;
        Lock lock = lock(subscription.code()).readLock(); lock.lock();
        try {
            if (!subscription.equals(subscriptions.get(key)) || !isStudent(subscription.principal()) || !member(subscription.code(), subscription.email())) { lock.unlock(); return null; }
            held.get().push(lock); return message;
        } catch (RuntimeException ex) { lock.unlock(); throw ex; }
    }
    /** Освобождает lock только после фактической обработки сообщения, включая исключение handler. */
    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        if (SimpMessageHeaderAccessor.wrap(message).getMessageType() != SimpMessageType.MESSAGE) return;
        Deque<Lock> locks = held.get(); if (!locks.isEmpty()) locks.pop().unlock(); if (locks.isEmpty()) held.remove();
    }
    /** Охватывает leave и commit write-lock, затем отзывает все подписки ученика на эту комнату. */
    public void leave(String code, String email, Runnable action) {
        code = QuizRoomCode.normalize(code); Lock lock = lock(code).writeLock(); lock.lock();
        try {
            action.run(); String normalized = code;
            subscriptions.entrySet().removeIf(e -> e.getValue().code().equals(normalized) && e.getValue().email().equalsIgnoreCase(email));
        } finally { lock.unlock(); }
    }
    /** При закрытии сокета освобождает все записи его подписок. */
    public void removeSession(String sessionId) { subscriptions.keySet().removeIf(k -> k.startsWith(sessionId + ":")); }
    private boolean member(String code, String email) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            var room = rooms.findByCodeIgnoreCase(code); var student = students.findByUserEmailIgnoreCase(email);
            return room.isPresent() && student.isPresent() && participants.findByRoomIdAndStudentProfileId(room.get().getId(), student.get().getId()).isPresent();
        }));
    }
    private void requireStudent(Principal principal) {
        if (!isStudent(principal)) throw new AccessDeniedException("Требуется ученик");
    }
    private boolean isStudent(Principal principal) {
        return principal instanceof Authentication auth && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
    }
    private ReentrantReadWriteLock lock(String code) { return locks[Math.floorMod(code.hashCode(), locks.length)]; }
    private static String key(String session, String subscription) { return session + ":" + subscription; }
    private record Subscription(String code, String email, Principal principal) {}
}
