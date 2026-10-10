package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/** Проверяет стабильную нормализацию отдельно от хранения исходного текста. */
class QuizTextNormalizerTest {
    private final QuizTextNormalizer normalizer = new QuizTextNormalizer();

    @Test
    void normalizesUnicodeCaseAndWhitespace() {
        assertThat(normalizer.normalize("  DISCOVER\u00A0  More  ")).isEqualTo("discover more");
        assertThat(normalizer.normalize("ＦＯＯ")).isEqualTo("foo");
    }
}
