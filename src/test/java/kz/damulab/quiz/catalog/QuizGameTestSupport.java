package kz.damulab.quiz.catalog;

import java.util.*;
import kz.damulab.content.*;
import kz.damulab.questions.*;
import kz.damulab.users.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Синтетические опубликованные банки для приёмки этапа 3; рабочие данные/БД не используются. */
@SpringBootTest
@ActiveProfiles("test")
abstract class QuizGameTestSupport {
    @Autowired QuizGameAdminService games;
    @Autowired QuizGameRepository gameRepository;
    @Autowired QuizGameVersionRepository versions;
    @Autowired QuizGameAvailabilityPolicy availability;
    @Autowired QuizGameContentDiagnostics diagnostics;
    @Autowired VocabularyAdminService vocabulary;
    @Autowired FactAdminService facts;
    @Autowired GradeRepository grades;
    @Autowired SubjectRepository subjects;
    @Autowired AppUserRepository users;
    @Autowired QuestionRepository questionRepository;
    @Autowired QuestionVersionRepository questionVersions;
    @Autowired jakarta.persistence.EntityManager entityManager;

    protected Long admin() { return users.findByEmailIgnoreCase("admin@damulab.kz").orElseThrow().getId(); }
    protected long revision(Long id) { return gameRepository.findById(id).orElseThrow().getAdminRevision(); }
    protected String code() { return "test-" + UUID.randomUUID(); }
    protected QuizGameForm form(QuizGameType type) {
        QuizGameForm f = new QuizGameForm(); f.setCode(code()); f.setGameType(type); f.setTitleRu("Тестовая игра"); f.setTitleKk("Сынақ ойыны");
        f.setDescriptionRu("Описание"); f.setDescriptionKk("Сипаттама"); f.setMinQuestionCount(1); f.setDefaultQuestionCount(1); f.setMaxQuestionCount(1); return f;
    }
    protected FactSet factSet(int count) {
        FactSetForm f = new FactSetForm(); f.setCode(code()); f.setTitleRu("Факты"); f.setTitleKk("Деректер"); f.setSourcePolicy("Проверяемые источники");
        FactSet set = facts.createSet(f, admin()); facts.transitionSet(set.getId(), EditorialStatus.NEEDS_REVIEW, admin()); facts.transitionSet(set.getId(), EditorialStatus.APPROVED, admin()); facts.transitionSet(set.getId(), EditorialStatus.PUBLISHED, admin());
        for (int i = 0; i < count; i++) publishFact(set, true, List.of()); return set;
    }
    protected FactStatement publishFact(FactSet set, boolean universal, List<Long> gradeIds) {
        FactStatementForm f = new FactStatementForm(); f.setSetId(set.getId()); f.setTextRu("Ложное утверждение"); f.setTextKk("Жалған тұжырым");
        f.setTruthValue(false); f.setExplanationRu("Объяснение"); f.setExplanationKk("Түсіндірме"); f.setSourceReference("Научный источник");
        f.setUniversalAudience(universal); f.setGradeIds(gradeIds); FactStatement s = facts.createStatement(f, admin());
        facts.submit(s.getId(), admin()); facts.approve(s.getId(), admin()); facts.publish(s.getId(), admin()); return s;
    }
    protected VocabularySet wordSet(int count) {
        VocabularySetForm f = new VocabularySetForm(); f.setCode(code()); f.setTitleRu("Слова"); f.setTitleKk("Сөздер");
        f.setSourceLanguage("en"); f.setTargetLanguage("ru"); f.setSourceReference("Словарь");
        VocabularySet set = vocabulary.createSet(f, admin()); vocabulary.submitSet(set.getId(), admin()); vocabulary.approveSet(set.getId(), admin()); vocabulary.publishSet(set.getId(), admin());
        for (int i = 0; i < count; i++) {
            VocabularyItemForm v = new VocabularyItemForm(); v.setSetId(set.getId()); v.setSourceText("word-" + i); v.setPrimaryAnswer("перевод-" + i);
            v.setMeaningNoteRu("Одно учебное значение"); v.setSourceReference("Словарь"); v.setUniversalAudience(true); v.setDifficulty(1);
            VocabularyItem item = vocabulary.createItem(v, admin()); vocabulary.submitItem(item.getId(), admin()); vocabulary.approveItem(item.getId(), admin()); vocabulary.publishItem(item.getId(), admin());
        } return set;
    }
    protected QuizGameForm factForm(int count) { QuizGameForm f = form(QuizGameType.TRUE_FALSE); f.setFactSetId(factSet(count).getId()); return f; }
    protected QuizGame createApproved(QuizGameForm form) {
        QuizGame game = games.create(form, admin()); approve(game.getId()); return game;
    }
    protected void approve(Long id) { games.transition(id, revision(id), "submit", admin()); games.transition(id, revision(id), "approve", admin()); }
    protected QuizGame publishGame(QuizGameForm form) { QuizGame game = createApproved(form); games.publish(game.getId(), revision(game.getId()), false, admin()); return game; }
    protected QuestionVersion subjectQuestion(Subject subject, QuestionType type, int difficulty, List<Long> gradeIds) {
        Question q = questionRepository.save(new Question(QuestionStatus.PUBLISHED, users.findById(admin()).orElseThrow()));
        QuestionVersion v = new QuestionVersion(q, 1, type, subject, null, difficulty, "Вопрос", "Сұрақ", "Разбор", "Түсіндірме", null, null, "Учебник", "[]", "{\"correctOptionIds\":[\"a\"]}");
        v.markPublished(); questionVersions.saveAndFlush(v);
        for (Long id : gradeIds) v.getGradeLinks().add(new QuestionVersionGrade(v, grades.findById(id).orElseThrow()));
        q.setCurrentVersion(v); entityManager.flush(); return v;
    }
}
