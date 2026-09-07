package kz.damulab.lectures;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Репозиторий лекций для административных и ученических сценариев. */
public interface LectureRepository extends JpaRepository<Lecture, Long>, JpaSpecificationExecutor<Lecture> {

    /** Не позволяет повторному агентскому импорту молча создать вторую лекцию. */
    boolean existsByExternalIdIgnoreCase(String externalId);

    /** Возвращает лекции для существующего административного списка. */
    List<Lecture> findByStatusOrderByUpdatedAtDesc(LectureStatus status);

    /**
     * Возвращает опубликованные лекции строго одной пары «предмет + класс».
     * Оба условия обязательны: без фильтра по классу ученик получил бы на одной
     * странице материалы разных учебных программ. Сортируем по дате добавления,
     * а связанные сущности загружаем сразу без отдельного запроса на каждую строку.
     */
    @Query("""
            select lecture
            from Lecture lecture
            join fetch lecture.currentVersion version
            join fetch version.topic topic
            join fetch topic.subject subject
            join fetch topic.grade grade
            where lecture.status = kz.damulab.lectures.LectureStatus.PUBLISHED
              and subject.id = :subjectId
              and grade.id = :gradeId
            order by lecture.createdAt desc
            """)
    List<Lecture> findPublishedBySubjectIdAndGradeIdOrderByCreatedAtDesc(
            @Param("subjectId") Long subjectId,
            @Param("gradeId") Long gradeId
    );

    /** Загружает одну опубликованную лекцию вместе с предметом и темой. */
    @Query("""
            select lecture
            from Lecture lecture
            join fetch lecture.currentVersion version
            join fetch version.topic topic
            join fetch topic.subject subject
            where lecture.id = :lectureId
              and lecture.status = kz.damulab.lectures.LectureStatus.PUBLISHED
            """)
    Optional<Lecture> findPublishedWithTopic(@Param("lectureId") Long lectureId);
}
