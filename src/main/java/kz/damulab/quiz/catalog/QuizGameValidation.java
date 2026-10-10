package kz.damulab.quiz.catalog;

import java.util.*;

/** Ограничения формы игры до SQL: типы, лимиты и две независимые политики класса/аудитории. */
public final class QuizGameValidation {
    /** Разрешённые варианты карточки из существующих стилевых решений. */
    public static final Set<String> CARD_STYLES = Set.of("default", "english", "kazakh", "math", "facts");
    private QuizGameValidation() {}

    /** Собирает структурные ошибки; отсутствие названий допускается только у черновика. */
    public static List<String> errors(QuizGameForm f, Set<Long> supportedGrades, boolean publication) {
        List<String> errors = new ArrayList<>();
        if (!validCode(f.getCode())) errors.add("Код: 2–96 строчных латинских букв, цифр, _ или -");
        if (f.getGameType() == null) errors.add("Выберите тип игры");
        if (!CARD_STYLES.contains(Objects.toString(f.getCardStyle(), ""))) errors.add("Выберите допустимый стиль карточки");
        if (f.getMinQuestionCount() < 1 || f.getMinQuestionCount() > f.getDefaultQuestionCount() || f.getDefaultQuestionCount() > f.getMaxQuestionCount()) errors.add("Количество раундов: 1 ≤ минимум ≤ по умолчанию ≤ максимум");
        if (f.getMinRoundSeconds() < 1 || f.getMinRoundSeconds() > f.getDefaultRoundSeconds() || f.getDefaultRoundSeconds() > f.getMaxRoundSeconds()) errors.add("Время: 1 ≤ минимум ≤ по умолчанию ≤ максимум");
        if (f.getMaxPlayers() < 1 || f.getMaxPlayers() > 4) errors.add("Максимум игроков должен быть от 1 до 4");
        if (f.getGradeSelectionMode() == null || f.getAudiencePolicy() == null) errors.add("Выберите обе политики класса и аудитории");
        else {
            if (f.getAudiencePolicy() == AudiencePolicy.ROOM_GRADE && f.getGradeSelectionMode() == GradeSelectionMode.NONE) errors.add("ROOM_GRADE требует PROFILE_DEFAULT или REQUIRED");
            if (f.getAudiencePolicy() == AudiencePolicy.PARTICIPANT_GRADES && f.getGradeSelectionMode() != GradeSelectionMode.NONE) errors.add("PARTICIPANT_GRADES требует NONE: классы берутся из профилей всех участников");
            boolean needsGrades = f.getAudiencePolicy() == AudiencePolicy.FIXED_GRADES || f.getGradeSelectionMode() != GradeSelectionMode.NONE;
            if (needsGrades && (f.getGradeIds() == null || f.getGradeIds().isEmpty())) errors.add("Укажите разрешённые классы");
            if (!needsGrades && f.getGradeIds() != null && !f.getGradeIds().isEmpty()) errors.add("Для этой политики список классов не используется; очистите его");
        }
        if (f.getGradeIds() == null || !supportedGrades.containsAll(f.getGradeIds())) errors.add("Список классов содержит неизвестные значения");
        if (f.getDifficulty() != null && (f.getDifficulty() < 1 || f.getDifficulty() > 5)) errors.add("Сложность должна быть от 1 до 5");
        if (publication && (QuizContentEligibility.blank(f.getTitleRu()) || QuizContentEligibility.blank(f.getTitleKk()))) errors.add("Для публикации нужны оба названия RU/KK");
        checkLength(errors, f.getTitleRu(), 255, "Название RU"); checkLength(errors, f.getTitleKk(), 255, "Название KK");
        checkLength(errors, f.getMechanicLabelRu(), 128, "Механика RU"); checkLength(errors, f.getMechanicLabelKk(), 128, "Механика KK");
        return errors;
    }
    private static void checkLength(List<String> errors, String value, int limit, String label) {
        if (value != null && value.length() > limit) errors.add(label + ": не более " + limit + " символов");
    }
    /** Единый алфавит стабильной игры для редакционной формы и create/replay API. */
    public static boolean validCode(String code) { return code != null && code.matches("[a-z0-9][a-z0-9_-]{1,95}"); }
}
