package kz.damulab.quiz;

import java.security.SecureRandom;
import java.util.Locale;
import kz.damulab.quiz.engine.QuizEngineException;
import org.springframework.stereotype.Component;

/** Единый формат исторических кодов QZxxxx для HTTP и точных STOMP topics. */
@Component
public class QuizRoomCode {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    /** Удаляет только внешние пробелы; внутренние пробелы и другой алфавит отклоняются. */
    public static String normalize(String value) {
        if (value == null) throw new QuizEngineException("ROOM_CODE_INVALID");
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("QZ[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}")) throw new QuizEngineException("ROOM_CODE_INVALID");
        return code;
    }
    /** Генерирует одну попытку; коллизии обрабатываются в новой транзакции create. */
    public String generate() {
        StringBuilder value = new StringBuilder("QZ");
        for (int i = 0; i < 4; i++) value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return value.toString();
    }
}
