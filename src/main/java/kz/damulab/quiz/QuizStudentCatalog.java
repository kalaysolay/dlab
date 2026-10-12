package kz.damulab.quiz;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import kz.damulab.content.*;
import kz.damulab.users.*;
import kz.damulab.quiz.catalog.*;
import kz.damulab.quiz.engine.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Пакетный ученический каталог. Использует общие аудиторию/диагностику; отдаёт только карточки, без банков и ключей. */
@Service
public class QuizStudentCatalog {
    private final QuizGameRepository games;
    private final QuizSubjectGameConfigRepository subjects;
    private final QuizWordGameConfigRepository words;
    private final QuizFactGameConfigRepository facts;
    private final QuizGameContentDiagnostics diagnostics;
    private final QuizGameAvailabilityPolicy availability;
    private final QuizAudienceResolver audience;
    private final StudentProfileRepository students;
    private final GradeRepository grades;
    /** Подключает существующие policies и пакетные репозитории. */
    public QuizStudentCatalog(QuizGameRepository games, QuizSubjectGameConfigRepository subjects,
            QuizWordGameConfigRepository words, QuizFactGameConfigRepository facts,
            QuizGameContentDiagnostics diagnostics, QuizGameAvailabilityPolicy availability,
            QuizAudienceResolver audience, StudentProfileRepository students, GradeRepository grades) {
        this.games=games; this.subjects=subjects; this.words=words; this.facts=facts;
        this.diagnostics=diagnostics; this.availability=availability; this.audience=audience; this.students=students; this.grades=grades;
    }
    /** Карточки сортируются по административному порядку и локализованному названию; число запросов не зависит от числа игр. */
    @Transactional(readOnly=true)
    public List<Card> read(String email, String locale) {
        var student=students.findByUserEmailIgnoreCase(email).orElseThrow(() -> new QuizEngineException("PROFILE_GRADE_REQUIRED"));
        var published=games.findPublishedCatalog(Pageable.unpaged());
        if (published.isEmpty()) return List.of();
        var ids=published.stream().map(g -> g.getPublishedVersion().getId()).toList();
        var sc=subjects.findByGameVersionIdIn(ids).stream().collect(Collectors.toMap(QuizSubjectGameConfig::getGameVersionId, v->v));
        var wc=words.findByGameVersionIdIn(ids).stream().collect(Collectors.toMap(QuizWordGameConfig::getGameVersionId, v->v));
        var fc=facts.findByGameVersionIdIn(ids).stream().collect(Collectors.toMap(QuizFactGameConfig::getGameVersionId, v->v));
        var allGrades=grades.findAllByOrderByGradeNoAsc();
        var supported=allGrades.stream().collect(Collectors.toMap(Grade::getGradeNo, Grade::getId));
        var data=diagnostics.prepare(); List<Card> cards=new ArrayList<>();
        for (var game:published) {
            if (!availability.visible(game)) continue;
            var v=game.getPublishedVersion(); String title=QuizSnapshotJson.localized(v.getTitleRu(),v.getTitleKk(),locale);
            if (title.isBlank()) continue;
            var form=new QuizGameForm(); form.setGameType(v.getGameType()); form.setAudiencePolicy(v.getAudiencePolicy());
            form.setGradeIds(List.copyOf(v.getGradeIds())); form.setMaxPlayers(v.getMaxPlayers());
            var s=sc.get(v.getId()); var w=wc.get(v.getId()); var f=fc.get(v.getId());
            if (s!=null) { form.setSubjectId(s.getSubject().getId()); form.setQuestionTypes(Set.copyOf(s.getQuestionTypes())); form.setDifficulty(s.getDifficulty()); }
            if (w!=null) { form.setVocabularySetId(w.getVocabularySet().getId()); form.setSourceLanguage(w.getSourceLanguage()); form.setTargetLanguage(w.getTargetLanguage()); }
            if (f!=null) form.setFactSetId(f.getFactSet().getId());
            boolean sourceValid=diagnostics.diagnose(form,data).errors().isEmpty();
            List<Choice> choices=new ArrayList<>();
            for (var grade:allGrades) if (v.getGradeIds().contains(grade.getId())) {
                choices.add(new Choice(grade.getId(),QuizSnapshotJson.localized(grade.getTitleRu(),grade.getTitleKk(),locale),
                        blocker(v,student,grade.getGradeNo(),form,data,supported,sourceValid)));
            }
            Long selected=v.getGradeSelectionMode()==GradeSelectionMode.PROFILE_DEFAULT ? supported.get(student.getGradeNo()) : null;
            if (selected!=null && !v.getGradeIds().contains(selected)) selected=null;
            String blocker;
            if (v.getGradeSelectionMode()==GradeSelectionMode.NONE) blocker=blocker(v,student,null,form,data,supported,sourceValid);
            else if (choices.stream().noneMatch(c->c.blocker()==null)) blocker=choices.isEmpty()?"INVALID_GRADE":choices.getFirst().blocker();
            else blocker=null; // REQUIRED выбирается прямо на карточке, без дополнительного экрана.
            cards.add(new Card(game.getCode(),title,QuizSnapshotJson.localized(v.getDescriptionRu(),v.getDescriptionKk(),locale),
                    v.getGameType().name(),QuizSnapshotJson.localized(v.getMechanicLabelRu(),v.getMechanicLabelKk(),locale),
                    v.getDefaultQuestionCount(),v.getDefaultRoundSeconds(),v.getMaxPlayers(),v.getGradeSelectionMode().name(),
                    v.getCardStyle(),selected,List.copyOf(choices),blocker,UUID.randomUUID().toString(),v.getCatalogOrder()));
        }
        cards.sort(Comparator.comparingInt(Card::order).thenComparing(Card::title).thenComparing(Card::code));
        return List.copyOf(cards);
    }
    private String blocker(QuizGameVersion v, StudentProfile student, Integer gradeNo, QuizGameForm f,
            QuizGameContentDiagnostics.Batch data, Map<Integer,Long> supported, boolean sourceValid) {
        if (!sourceValid) return "GAME_CONFIGURATION_INVALID";
        try {
            var room=new QuizRoom("QZABCD",student,null,null,student.getPreferredLanguage(),null,v.getDefaultQuestionCount(),v.getDefaultRoundSeconds(),v.getMaxPlayers());
            room.attachGameVersion(v,OffsetDateTime.now().plusMinutes(30)); room.initializeAudienceGrade(gradeNo);
            var actual=audience.resolve(room,List.of(new QuizParticipant(room,student,student.getUser().getFullName(),true)),supported);
            return diagnostics.coverage(f,actual.gradeIds(),data).playable()<v.getDefaultQuestionCount()?"INSUFFICIENT_PUBLISHED_CONTENT":null;
        } catch (QuizEngineException ex) { return QuizRoomService.externalCode(ex.getCode()); }
    }
    /** Безопасная подпись разрешённого класса и причина блокировки для текущего ученика. */
    public record Choice(Long id,String title,String blocker) {}
    /** Только метаданные карточки; form key обеспечивает безопасный повтор POST. */
    public record Card(String code,String title,String description,String type,String mechanic,int count,int seconds,int maxPlayers,
            String gradeMode,String style,Long selectedGradeId,List<Choice> grades,String blocker,String key,int order) {}
}
