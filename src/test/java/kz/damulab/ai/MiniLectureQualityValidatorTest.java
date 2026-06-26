package kz.damulab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MiniLectureQualityValidatorTest {

    private static final MiniLectureGenerationRequest PERCENT_REQUEST = new MiniLectureGenerationRequest(
            "Математика", "Математика", "4 класс", "4 сынып", 4,
            "Проценты", "Пайыздар",
            "Найди 10% от числа 300.", "300 санынан 10%-ын таб.",
            "-", "-", "30", "30"
    );

    @Test
    void rejectsFormulaOnlyBriefAnswer() {
        MiniLectureStructuredPayload payload = payload(
                ru("10% — это одна десятая числа. 300 : 10 = 30, значит 10% от 300 равно 30."),
                kz("10% — бұл санның оннан бірі. 300 : 10 = 30.")
        );

        assertThatThrownBy(() -> MiniLectureQualityValidator.validate(payload, PERCENT_REQUEST))
                .isInstanceOf(AiProviderException.class)
                .hasFieldOrPropertyWithValue("code", "ai_mini_lecture_too_brief");
    }

    @Test
    void acceptsExpandedPercentLecture() {
        MiniLectureStructuredPayload payload = payload(
                ru("""
                        Процент показывает, сколько частей из 100 мы берём от числа.
                        Сначала удобно найти 1% — разделить число на 100.
                        Потом умножить 1% на нужное количество процентов.
                        """),
                kz("""
                        Пайыз — бұл 100-ден қанша бөлікті алатынымызды көрсетеді.
                        Алдымен 1% табамыз — санды 100-ге бөлеміз.
                        Содан кейін 1%-ды қажетті пайызға көбейтеміз.
                        """)
        );

        MiniLectureQualityValidator.validate(payload, PERCENT_REQUEST);
    }

    private static MiniLectureStructuredPayload payload(String ruTheory, String kzTheory) {
        MiniLectureLangBlock ru = fullBlockRu("Проценты", ruTheory);
        MiniLectureLangBlock kz = fullBlockKk("Пайыздар", kzTheory);
        return new MiniLectureStructuredPayload(ru, kz);
    }

    private static String ru(String theory) {
        return theory;
    }

    private static String kz(String theory) {
        return theory;
    }

    private static MiniLectureLangBlock fullBlockRu(String title, String theory) {
        return new MiniLectureLangBlock(
                title,
                theory,
                """
                Шаг 1: Перечитаем задачу — нужно найти 10% от числа 300.
                Шаг 2: Найдём 1% от 300. Для этого 300 : 100 = 3. Значит, 1% от 300 равен 3.
                Шаг 3: Чтобы получить 10%, умножим 1% на 10: 3 · 10 = 30.
                Шаг 4: Проверим ответ: 30 — это действительно 10% от 300.
                """,
                """
                Часто путают и сразу делят на 10, забывая объяснить, почему это работает только для 10%, а не для 15% или 20%.
                """,
                """
                Пример: найдём 20% от 50.
                Шаг 1: 1% от 50 = 50 : 100 = 0,5.
                Шаг 2: 20% = 0,5 · 20 = 10.
                Шаг 3: Ответ — 10, это пятая часть числа 50.
                """,
                "Сначала ищи 1%, потом умножай на нужный процент."
        );
    }

    private static MiniLectureLangBlock fullBlockKk(String title, String theory) {
        return new MiniLectureLangBlock(
                title,
                theory,
                """
                1-шаг: Есепті оқимыз — 300 санынан 10% табу керек.
                2-шаг: 1% табамыз: 300 : 100 = 3. Яғни, 300-дің 1% = 3.
                3-шаг: 10% алу үшін 1%-ды 10-ға көбейтеміз: 3 · 10 = 30.
                4-шаг: Жауапты тексереміз: 30 — 300-дің 10%-ы.
                """,
                """
                Кейде 10% үшін ғана 10-ға бөлуге асығады, ал 1% неге керек екенін түсіндірмейді — бұл 15% немесе 20% үшін жарамайды.
                """,
                """
                Мысал: 50 санынан 20% табайық.
                1-шаг: 1% = 50 : 100 = 0,5.
                2-шаг: 20% = 0,5 · 20 = 10.
                3-шаг: Жауап — 10, бұл 50 санının бесінші бөлігі.
                """,
                "Алдымен 1% таб, sonra қажетті пайызға көбейт."
        );
    }
}
