package kz.damulab.quiz.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Читает историю видимости; сервис только добавляет события и не удаляет их. */
public interface QuizGameVisibilityEventRepository extends JpaRepository<QuizGameVisibilityEvent, Long> {
    /** Возвращает действия над одной игрой в порядке записи. */
    List<QuizGameVisibilityEvent> findByGameIdOrderByIdAsc(Long gameId);
}
