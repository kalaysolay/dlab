package kz.damulab.questions;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionVersionRepository extends JpaRepository<QuestionVersion, Long> {

    boolean existsByAtomicSkillId(Long atomicSkillId);

    Optional<QuestionVersion> findTopByQuestionIdOrderByVersionNoDesc(Long questionId);

    @Query("""
            select coalesce(max(v.versionNo), 0)
            from QuestionVersion v
            where v.question.id = :questionId
            """)
    int findMaxVersionNoByQuestionId(@Param("questionId") Long questionId);

    @Query("""
            select v
            from Question question
            join question.currentVersion v
            where question.status = kz.damulab.questions.QuestionStatus.PUBLISHED
              and (:subjectId is null or v.subject.id = :subjectId)
              and (:gradeId is null or exists (
                  select 1 from QuestionVersionGrade g
                  where g.questionVersion = v and g.grade.id = :gradeId))
              and (:difficulty is null or v.difficulty = :difficulty)
            order by v.createdAt asc
            """)
    List<QuestionVersion> findPublishedForTest(
            @Param("subjectId") Long subjectId,
            @Param("gradeId") Long gradeId,
            @Param("difficulty") Integer difficulty,
            Pageable pageable
    );

    @Query("""
            select count(question.id)
            from Question question
            join question.currentVersion v
            where question.status = kz.damulab.questions.QuestionStatus.PUBLISHED
              and v.subject.id = :subjectId
              and exists (
                  select 1 from QuestionVersionGrade g
                  where g.questionVersion = v and g.grade.id = :gradeId)
            """)
    long countPublishedForSubjectAndGrade(
            @Param("subjectId") Long subjectId,
            @Param("gradeId") Long gradeId
    );

    @Query("""
            select v.subject.id, link.grade.id, count(question.id)
            from Question question
            join question.currentVersion v
            join v.gradeLinks link
            where question.status = kz.damulab.questions.QuestionStatus.PUBLISHED
            group by v.subject.id, link.grade.id
            having count(question.id) >= :minCount
            """)
    List<Object[]> countPublishedGroupedBySubjectAndGrade(@Param("minCount") long minCount);

    @Query("""
            select v
            from Question question
            join question.currentVersion v
            where question.status = kz.damulab.questions.QuestionStatus.PUBLISHED
              and exists (
                  select 1 from QuestionVersionTopic vt
                  where vt.questionVersion = v and vt.topic.id = :topicId)
            order by v.createdAt asc
            """)
    List<QuestionVersion> findPublishedByTopicId(
            @Param("topicId") Long topicId,
            Pageable pageable
    );

    @Query("""
            select question.id, version.id, count(evaluation.id),
                   coalesce(sum(case when evaluation.correct = false then 1 else 0 end), 0)
            from Question question
            join question.currentVersion version
            left join TestSessionQuestion sessionQuestion on sessionQuestion.questionVersion = version
            left join AnswerEvaluation evaluation on evaluation.sessionQuestion = sessionQuestion
            group by question.id, version.id
            """)
    List<Object[]> aggregateCurrentVersionHealth();

    @Query("""
            select question.id, version.id,
                   coalesce(sum(case when result.percent >= 80 then 1 else 0 end), 0),
                   coalesce(sum(case when result.percent >= 80 and evaluation.correct = true then 1 else 0 end), 0),
                   coalesce(sum(case when result.percent < 60 then 1 else 0 end), 0),
                   coalesce(sum(case when result.percent < 60 and evaluation.correct = true then 1 else 0 end), 0)
            from Question question
            join question.currentVersion version
            left join TestSessionQuestion sessionQuestion on sessionQuestion.questionVersion = version
            left join AnswerEvaluation evaluation on evaluation.sessionQuestion = sessionQuestion
            left join TestResult result on result.session = sessionQuestion.session
            group by question.id, version.id
            """)
    List<Object[]> aggregateCurrentVersionDiscrimination();
}
