package kz.damulab.quiz.catalog;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Строит стабильный поисковый ключ, не изменяя сохранённый и показываемый редактором текст. */
@Component
public class QuizTextNormalizer {
    /** Нормализует Unicode, регистр и последовательности пробелов для поиска дублей и сравнений. */
    public String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip().toLowerCase(Locale.ROOT);
        return normalized.replaceAll("\\s+", " ");
    }
}
