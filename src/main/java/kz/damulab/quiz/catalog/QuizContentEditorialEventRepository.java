package kz.damulab.quiz.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Читает и дописывает редакционный аудит; существующие события не обновляются и не удаляются. */
public interface QuizContentEditorialEventRepository extends JpaRepository<QuizContentEditorialEvent, Long> {
    /** Возвращает историю объекта в хронологическом порядке. */
    List<QuizContentEditorialEvent> findByContentTypeAndContentIdOrderByCreatedAtAsc(QuizContentType type, Long contentId);
}
