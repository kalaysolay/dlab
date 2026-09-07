package kz.damulab.content;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import kz.damulab.lectures.LectureSubjectGradeView;

/** Репозиторий учебных предметов и простых выборок для пользовательских каталогов. */
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    /** Возвращает все предметы для административных форм. */
    List<Subject> findAllByOrderByTitleRuAsc();

    /** Находит предмет по его стабильному коду. */
    Optional<Subject> findByCodeIgnoreCase(String code);

    /**
     * Возвращает пары «предмет + класс», в которых есть опубликованные лекции.
     * Группировка одновременно исключает дубли и считает уроки на каждой плитке;
     * черновики и архивные материалы не создают пустой раздел для ученика.
     */
    @Query("""
            select new kz.damulab.lectures.LectureSubjectGradeView(
                subject.id, subject.code, subject.titleRu, subject.titleKk,
                subject.iconStorageKey,
                grade.id, grade.gradeNo, grade.titleRu, grade.titleKk,
                count(lecture.id)
            )
            from Lecture lecture
            join lecture.currentVersion version
            join version.topic topic
            join topic.subject subject
            join topic.grade grade
            where lecture.status = kz.damulab.lectures.LectureStatus.PUBLISHED
            group by subject.id, subject.code, subject.titleRu, subject.titleKk, subject.iconStorageKey,
                     grade.id, grade.gradeNo, grade.titleRu, grade.titleKk
            order by subject.titleRu asc, grade.gradeNo asc
            """)
    List<LectureSubjectGradeView> findSubjectGradesWithPublishedLectures();
}
