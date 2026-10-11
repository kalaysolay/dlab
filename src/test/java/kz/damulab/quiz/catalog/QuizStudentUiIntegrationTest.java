package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import kz.damulab.quiz.*;
import kz.damulab.quiz.engine.*;
import kz.damulab.questions.*;
import kz.damulab.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** SSR и настоящий HTTP-сервер на изолированных test-данных; committed фикстуры архивируются без удаления истории. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"damulab.quiz.game-catalog-enabled=true","damulab.quiz.timeout-worker-delay-ms=600000"})
@AutoConfigureMockMvc
@Import(QuizStudentUiIntegrationTest.TimeConfig.class)
class QuizStudentUiIntegrationTest extends QuizGameTestSupport {
    @Autowired MockMvc mvc;
    @Autowired QuizStudentCatalog catalog;
    @Autowired QuizRoomService rooms;
    @Autowired QuizRoomRepository roomRepository;
    @Autowired StudentProfileRepository profiles;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper mapper;
    @Autowired UiClock clock;
    @LocalServerPort int port;
    private final Set<Long> ownGames=new HashSet<>();
    private String host;
    @BeforeEach void init() { clock.offset=0; host=profile(5,"ru"); }
    @AfterEach void cleanup() {

        tx(()->{
            for(var r:entityManager.createQuery("select r from QuizRoom r join r.hostStudentProfile p join p.user u where u.email=:email",QuizRoom.class).setParameter("email",host).getResultList())
                if(r.getStatus()==QuizRoomStatus.WAITING) r.cancel(OffsetDateTime.now(clock));
            for(Long id:ownGames) {
                var g=gameRepository.findById(id).orElseThrow();
                for(var c:entityManager.createQuery("select c from QuizWordGameConfig c where c.gameVersion.game.id=:id",QuizWordGameConfig.class).setParameter("id",id).getResultList())
                    if(c.getVocabularySet().getStatus()!=EditorialStatus.ARCHIVED) vocabulary.archiveSet(c.getVocabularySet().getId(),admin());
                for(var c:entityManager.createQuery("select c from QuizFactGameConfig c where c.gameVersion.game.id=:id",QuizFactGameConfig.class).setParameter("id",id).getResultList())
                    if(c.getFactSet().getStatus()!=EditorialStatus.ARCHIVED) facts.archiveSet(c.getFactSet().getId(),admin());
                if(g.getStatus()!=EditorialStatus.ARCHIVED) games.archive(id,revision(id),admin());
            }
            return null;
        });
    }
    @Test void catalogIsLocalizedOrderedEscapedAndHidesPrivateGames() throws Exception {
        var one=tx(()->{var f=factForm(1); f.setTitleRu("Яблоко <script>alert(1)</script>"); f.setTitleKk("Алма"); f.setCatalogOrder(-10); return publish(f);});
        var two=tx(()->{var f=factForm(1); f.setTitleRu("Абрикос"); f.setTitleKk("Өрік"); f.setCatalogOrder(-10); return publish(f);});
        var hidden=tx(()->publish(factForm(1))); tx(()->{games.setVisibility(hidden.getId(),revision(hidden.getId()),false,admin()); return null;});
        var list=catalog.read(host,"ru"); assertThat(list.stream().filter(c->c.code().equals(one.getCode())||c.code().equals(two.getCode())).map(QuizStudentCatalog.Card::title)).containsExactly("Абрикос","Яблоко <script>alert(1)</script>");
        assertThat(list).noneMatch(c->c.code().equals(hidden.getCode()));
        String html=mvc.perform(get("/student/quiz").with(user(host).roles("STUDENT"))).andExpect(status().isOk()).andExpect(view().name("student/quiz-games")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>alert(1)</script>","answerKeySnapshot","Ложное утверждение");
        mvc.perform(get("/student/quiz").param("lang","kk").with(user(host).roles("STUDENT"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Алма")));
    }
    @Test void cardPostIsCsrfProtectedIdempotentAndOnlyStudentsHaveAccess() throws Exception {
        var game=tx(()->publish(factForm(1))); String key=UUID.randomUUID().toString();
        long before=roomRepository.count();
        // Общий SSR handler при отказе CSRF ведёт на access-denied; API возвращает 403.
        mvc.perform(post("/student/quiz/games/rooms").with(user(host).roles("STUDENT")).param("gameCode",game.getCode()).param("idempotencyKey",key)).andExpect(redirectedUrl("/access-denied"));
        assertThat(roomRepository.count()).isEqualTo(before);
        String redirect=mvc.perform(post("/student/quiz/games/rooms").with(user(host).roles("STUDENT")).with(csrf()).param("gameCode",game.getCode()).param("idempotencyKey",key))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(post("/student/quiz/games/rooms").with(user(host).roles("STUDENT")).with(csrf()).param("gameCode",game.getCode()).param("idempotencyKey",key)).andExpect(redirectedUrl(redirect));
        for(String role:List.of("PARENT","ADMIN")) mvc.perform(get("/student/quiz/games").with(user(host).roles(role))).andExpect(redirectedUrl("/access-denied"));
        mvc.perform(get("/student/quiz/games")).andExpect(status().is3xxRedirection());
    }
    @Test void missingProfileGradeHasAnActionableCardAndUniversalDoesNotNeedIt() {
        String missing=profile(null,"ru"); var universal=tx(()->publish(factForm(1)));
        var restricted=tx(()->{var f=factForm(1); f.setAudiencePolicy(AudiencePolicy.PARTICIPANT_GRADES); return publish(f);});
        var cards=catalog.read(missing,"ru");
        assertThat(cards.stream().filter(c->c.code().equals(universal.getCode())).findFirst().orElseThrow().blocker()).isNull();
        assertThat(cards.stream().filter(c->c.code().equals(restricted.getCode())).findFirst().orElseThrow().blocker()).isEqualTo("PROFILE_GRADE_REQUIRED");
    }
    @Test void fixedGradeFiveCardCanBeCreatedAndPlayedByGradeFourHost() throws Exception {
        tx(()->{ profiles.findByUserEmailIgnoreCase(host).orElseThrow().update(4,"ru"); return null; });
        var game=tx(()->{var f=factForm(1); f.setAudiencePolicy(AudiencePolicy.FIXED_GRADES);
            f.setGradeIds(List.of(grades.findAllByOrderByGradeNoAsc().stream().filter(g->g.getGradeNo()==5).findFirst().orElseThrow().getId())); return publish(f);});
        assertThat(catalog.read(host,"ru").stream().filter(c->c.code().equals(game.getCode())).findFirst().orElseThrow().blocker()).isNull();
        String redirect=mvc.perform(post("/student/quiz/games/rooms").with(user(host).roles("STUDENT")).with(csrf())
                .param("gameCode",game.getCode()).param("idempotencyKey",UUID.randomUUID().toString()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        String code=redirect.substring(redirect.lastIndexOf('/')+1);
        var active=rooms.start(code,host);
        rooms.answer(code,host,new QuizGameRoomRequest.Answer(active.activeRoundId(),mapper.readTree("false"),false));
        clock.offset=25;
        assertThat(rooms.results(code,host).me().correctAnswers()).isEqualTo(1);
        assertThat(tx(()->profiles.findByUserEmailIgnoreCase(host).orElseThrow().getGradeNo())).isEqualTo(4);
        assertThat(tx(()->Set.copyOf(roomRepository.findByCodeIgnoreCase(code).orElseThrow().getAudienceGrades()))).containsExactly(5);
    }
    @Test void obsoleteFlagDoesNotSwitchUiOrRoomHistory() throws Exception {
        var g=tx(()->publish(factForm(1))); var r=create(g.getCode());
        mvc.perform(get("/student/quiz").with(user(host).roles("STUDENT"))).andExpect(view().name("student/quiz-games"));
        mvc.perform(get("/student/quiz/games").with(user(host).roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(get("/student/quiz/rooms/"+r.code()).with(user(host).roles("STUDENT"))).andExpect(status().isOk()).andExpect(view().name("student/quiz-game-room"));
    }
    @Test void outsiderCannotReadRoomResultsOrPinnedGradeChoices() throws Exception {
        var g=tx(()->publish(factForm(1))); var r=create(g.getCode()); String outsider=profile(5,"ru");
        mvc.perform(get("/student/quiz/rooms/"+r.code()).with(user(outsider).roles("STUDENT"))).andExpect(status().isForbidden());
        assertThatThrownBy(()->rooms.allowedGradeIds(r.code(),outsider)).isInstanceOf(QuizEngineException.class);
        mvc.perform(get("/student/quiz/rooms/"+r.code()+"/results").with(user(host).roles("STUDENT"))).andExpect(status().isConflict());
        mvc.perform(get("/student/quiz/join").param("code","<script>").with(user(host).roles("STUDENT"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("value=\"<script>"))));
    }
    @ParameterizedTest @EnumSource(QuestionType.class)
    void allSubjectFormatsRenderSafeShellAndCurrentWindow(QuestionType type) throws Exception {
        var game=subject(type); var r=create(game.getCode()); var active=rooms.start(r.code(),host);
        assertThat(active.currentRound().format()).isEqualTo(type.name());
        mvc.perform(get("/student/quiz/rooms/"+r.code()).with(user(host).roles("STUDENT"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("quiz-games.js")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("answerKey"))));
    }
    @Test void finishedCancelledAndExpiredScreensStayAccessibleAndReplayUsesCurrentPublication() throws Exception {
        var g=tx(()->publish(factForm(1))); var r=create(g.getCode()); var active=rooms.start(r.code(),host);
        rooms.answer(r.code(),host,new QuizGameRoomRequest.Answer(active.activeRoundId(),mapper.readTree("false"),false)); clock.offset=25;
        mvc.perform(get("/student/quiz/rooms/"+r.code()+"/results").with(user(host).roles("STUDENT"))).andExpect(status().isOk()).andExpect(model().attributeExists("replayCard"));
        assertThat(rooms.results(r.code(),host).me().correctAnswers()).isEqualTo(1);
        tx(()->{games.setVisibility(g.getId(),revision(g.getId()),false,admin()); return null;});
        mvc.perform(get("/student/quiz/rooms/"+r.code()+"/results").with(user(host).roles("STUDENT"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Повтор сейчас недоступен")));
        var other=tx(()->publish(factForm(1))); var cancelled=create(other.getCode()); rooms.cancel(cancelled.code(),host);
        mvc.perform(get("/student/quiz/rooms/"+cancelled.code()).with(user(host).roles("STUDENT"))).andExpect(status().isOk());
        var expired=create(other.getCode()); clock.offset+=1801;
        mvc.perform(get("/student/quiz/rooms/"+expired.code()).with(user(host).roles("STUDENT"))).andExpect(status().isOk()).andExpect(model().attributeExists("room"));
        assertThat(rooms.state(expired.code(),host).status()).isEqualTo("expired");
    }
    @ParameterizedTest @EnumSource(QuestionType.class)
    void customRoomUsesAllExistingQuestionFormatsAndSelectedLanguage(QuestionType type) throws Exception {
        var game=subject(type);
        Long subjectId=tx(()->entityManager.createQuery("select c.subject.id from QuizSubjectGameConfig c where c.gameVersion.game.code=:code",Long.class).setParameter("code",game.getCode()).getSingleResult());
        Long gradeId=tx(()->grades.findAllByOrderByGradeNoAsc().stream().filter(g->g.getGradeNo()==5).findFirst().orElseThrow().getId());
        var room=rooms.createCustom(host,new QuizGameRoomRequest.CustomCreate(subjectId,gradeId,"kk",1,10,UUID.randomUUID().toString()));
        var active=rooms.start(room.code(),host);
        assertThat(active.gameCode()).isNull();
        assertThat(active.currentRound().format()).isEqualTo(type.name());
        assertThat(active.currentRound().locale()).isEqualTo("kk");
        assertThat(active.currentRound().prompt()).startsWith("Сұрақ");
        rooms.answer(room.code(),host,new QuizGameRoomRequest.Answer(active.activeRoundId(),null,true));
        clock.offset=11;
        assertThat(rooms.results(room.code(),host).review().getFirst().skipped()).isTrue();
        mvc.perform(get("/student/quiz/rooms/"+room.code()+"/results").with(user(host).roles("STUDENT"))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/student/quiz/create")));
    }
    @Test void catalogUsesBoundedQueriesForMultipleCards() {
        var set=tx(()->factSet(1)); tx(()->{var f=form(QuizGameType.TRUE_FALSE); f.setFactSetId(set.getId()); publish(f); return null;});
        var stats=entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics(); boolean previous=stats.isStatisticsEnabled(); stats.setStatisticsEnabled(true);
        try {
            stats.clear(); catalog.read(host,"ru"); long one=stats.getPrepareStatementCount();
            tx(()->{for(int i=0;i<5;i++){var f=form(QuizGameType.TRUE_FALSE); f.setFactSetId(set.getId()); publish(f);} return null;});
            stats.clear(); catalog.read(host,"ru"); assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(one+1);
        } finally {stats.setStatisticsEnabled(previous);}
    }
    @Test void allGameTypesHaveRealServerPagesAndOptionalBrowserAcceptance() throws Exception {
        var codes=new LinkedHashMap<String,String>();
        codes.put("TRUE_FALSE",tx(()->publish(factForm(1))).getCode());
        codes.put("WORD_TRANSLATION",tx(()->{var f=form(QuizGameType.WORD_TRANSLATION); f.setVocabularySetId(wordSet(4).getId()); f.setSourceLanguage("en"); f.setTargetLanguage("ru"); return publish(f);}).getCode());
        for(var type:QuestionType.values()) codes.put(type.name(),subject(type).getCode());
        assertThat(catalog.read(host,"ru").stream().map(QuizStudentCatalog.Card::code)).containsAll(codes.values());
        mvc.perform(get("/student/quiz/games").with(user(host).roles("STUDENT"))).andExpect(status().isOk());
        if("1".equals(System.getenv("QUIZ_UI_SMOKE"))) {
            // Опционально удерживает этот же изолированный test-сервер для реальной браузерной приёмки.
            // После marker/тайм-аута обычный @AfterEach архивирует только свои банки и игры.
            String run=UUID.randomUUID().toString(), guest=profile(5,"kk");
            // Legacy-страницы проверяются на том же синтетическом предмете; история не очищается.
            Long subjectId=tx(()->entityManager.createQuery("select c.subject.id from QuizSubjectGameConfig c where c.gameVersion.game.code=:code",Long.class).setParameter("code",codes.get("SCQ")).getSingleResult());
            Long gradeId=tx(()->grades.findAllByOrderByGradeNoAsc().stream().filter(g->g.getGradeNo()==5).findFirst().orElseThrow().getId());
            var legacyRoom=rooms.createCustom(host,new QuizGameRoomRequest.CustomCreate(subjectId,gradeId,"kk",1,120,UUID.randomUUID().toString()));
            Path info=Path.of(".run-logs/quiz-ui-smoke-info.json"), done=Path.of(".run-logs/quiz-ui-smoke-done"), time=Path.of(".run-logs/quiz-ui-smoke-time"), flag=Path.of(".run-logs/quiz-ui-smoke-flag");
            Files.createDirectories(info.getParent()); Files.writeString(info,mapper.writeValueAsString(Map.of("run",run,"port",port,"host",host,"guest",guest,"games",codes,"legacyRoom",legacyRoom.code())));
            long deadline=System.nanoTime()+Duration.ofMinutes(30).toNanos();
            while(System.nanoTime()<deadline && !(Files.exists(done)&&Files.readString(done).trim().equals(run))) {
                if(Files.exists(time)){var parts=Files.readString(time).trim().split(":"); if(parts.length==2&&parts[0].equals(run)) clock.offset=Long.parseLong(parts[1]);}
                Thread.sleep(500);
            }
        }
    }
    private QuizGame subject(QuestionType type) {
        return tx(()->{
            var subject=subjects.save(new kz.damulab.content.Subject("ui-"+UUID.randomUUID(),"Предмет","Пән","Описание","Сипаттама"));
            var grade=grades.findAllByOrderByGradeNoAsc().stream().filter(g->g.getGradeNo()==5).findFirst().orElseThrow();
            var source=subjectQuestion(subject,type,2,List.of(grade.getId()));
            String options=switch(type){case SCQ,MCQ->"[{\"label\":\"A\",\"textRu\":\"Первый\",\"textKk\":\"Бірінші\"},{\"label\":\"B\",\"textRu\":\"Второй\",\"textKk\":\"Екінші\"}]"; case MATCHING->"[{\"leftRu\":\"1\",\"leftKk\":\"1\",\"rightRu\":\"Один\",\"rightKk\":\"Бір\"},{\"leftRu\":\"2\",\"leftKk\":\"2\",\"rightRu\":\"Два\",\"rightKk\":\"Екі\"}]"; case FILL_IN->"[]";};
            String key=switch(type){case SCQ->"[\"B\"]";case MCQ->"[\"A\",\"B\"]";case MATCHING->"{\"pairs\":[{\"left\":\"1\",\"right\":\"Один\"},{\"left\":\"2\",\"right\":\"Два\"}]}";case FILL_IN->"{\"[[1]]\":{\"answer\":\"30\",\"mode\":\"EXACT\"}}";};
            source.replaceContent(new QuestionVersion(source.getQuestion(),1,type,subject,null,2,"Вопрос [[1]]","Сұрақ [[1]]","Разбор","Түсіндірме",null,null,"Учебник",options,key)); entityManager.flush();
            var f=form(QuizGameType.SUBJECT_QUIZ); f.setTitleRu(type.name()+": предмет"); f.setTitleKk(type.name()+": пән"); f.setSubjectId(subject.getId()); f.setQuestionTypes(Set.of(type)); f.setDifficulty(2); f.setAudiencePolicy(AudiencePolicy.FIXED_GRADES); f.setGradeIds(List.of(grade.getId())); return publish(f);
        });
    }
    private QuizGame publish(QuizGameForm f) {
        if("1".equals(System.getenv("QUIZ_UI_SMOKE"))){f.setDefaultRoundSeconds(120);f.setMaxRoundSeconds(120);}
        var g=publishGame(f); ownGames.add(g.getId()); return g;
    }
    private String profile(Integer grade,String language) {
        return tx(()->{String email=UUID.randomUUID()+"@ui.test"; var u=new AppUser(email,passwords.encode("quiz-ui-test"),language.equals("kk")?"Қонақ":"Организатор",null); u.addRole(roles.findByCode(RoleCode.STUDENT).orElseThrow()); users.save(u); profiles.save(new StudentProfile(u,grade,language)); return email;});
    }
    private QuizGameRoomResponse create(String code) {return rooms.create(host,new QuizGameRoomRequest.Create(code,null,null,null,UUID.randomUUID().toString()));}
    private <T>T tx(Supplier<T> action){return new TransactionTemplate(manager).execute(s->action.get());}
    /** В обычных тестах ход времени можно ускорить; в браузере offset меняется только явным test-marker. */
    static class UiClock extends Clock {
        volatile long offset; @Override public ZoneId getZone(){return ZoneOffset.UTC;} @Override public Clock withZone(ZoneId zone){return this;} @Override public Instant instant(){return Instant.now().plusSeconds(offset);}
    }
    @TestConfiguration static class TimeConfig {@Bean @Primary UiClock uiClock(){return new UiClock();}}
}
