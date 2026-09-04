package kz.damulab.lectures;

/**
 * Состояние изучения лекции конкретным учеником.
 *
 * <p>Статус {@link #NOT_STARTED} вычисляется, когда записи прогресса ещё нет. В базу
 * сохраняются только начатые и завершённые лекции.</p>
 */
public enum StudentLectureStatus {
    NOT_STARTED("not_started"),
    IN_PROGRESS("in_progress"),
    DONE("done");

    private final String apiValue;

    StudentLectureStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    /** Возвращает стабильное значение статуса для HTML и будущего API. */
    public String apiValue() {
        return apiValue;
    }
}
