package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к VocabularyItemAnswer; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface VocabularyItemAnswerRepository extends JpaRepository<VocabularyItemAnswer, Long> {
    /** Загружает варианты сразу для всех выбранных редакций; не создаёт N+1. */
    List<VocabularyItemAnswer> findByItemVersionIdIn(Collection<Long> versionIds);
}
