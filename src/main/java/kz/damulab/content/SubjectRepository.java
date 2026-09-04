package kz.damulab.content;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import kz.damulab.lectures.LectureSubjectView;

/** Репозиторий учебных предметов и простых выборок для пользовательских каталогов. */
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    /** Возвращает все предметы для административных форм. */
    List<Subject> findAllByOrderByTitleRuAsc();

    /** Находит предмет по его стабильному коду. */
    Optional<Subject> findByCodeIgnoreCase(String code);

    /**
     * Возвращает только предметы с опубликованными лекциями. Группировка одновременно
     * исключает дубли и считает число лекций для подписи на плитке.
     */
    @Query("""
            select new kz.damulab.lectures.LectureSubjectView(
                subject.id, subject.code, subject.titleRu, subject.titleKk,
                subject.iconStorageKey, count(lecture.id)
            )
            from Lecture lecture
            join lecture.currentVersion version
            join version.topic topic
            join topic.subject subject
            where lecture.status = kz.damulab.lectures.LectureStatus.PUBLISHED
            group by subject.id, subject.code, subject.titleRu, subject.titleKk, subject.iconStorageKey
            order by subject.titleRu asc
            """)
    List<LectureSubjectView> findWithPublishedLectures();
}
