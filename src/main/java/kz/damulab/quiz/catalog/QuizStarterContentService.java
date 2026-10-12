package kz.damulab.quiz.catalog;

import java.io.IOException;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.content.*;
import kz.damulab.questions.*;
import kz.damulab.users.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Явная редакционная загрузка starter-v1 и заказанного пакета о животных через обычные сервисы публикации.
 * Не вызывается при startup/Flyway и не включает damulab.quiz.game-catalog-enabled.
 * Источники, аудитория и протокол проверки описаны в feature/solo-vocabulary-quiz/STARTER_CONTENT.md.
 */
@Service
public class QuizStarterContentService {
    /** Стабильные коды проверенного пакета; повтор загрузки не создаёт новые редакции под теми же кодами. */
    public static final List<String> CODES = List.of("starter-en-numbers-v1", "starter-kk-numbers-v1", "starter-math-sums-v1", "starter-space-facts-v1");
    /** Дополнительные двуязычные игры, явно заказанные пользователем; основной starter остаётся отдельным пакетом. */
    public static final List<String> ANIMAL_CODES = List.of("animal-wonders-v1", "ocean-animals-v1");
    private final ObjectMapper mapper;
    private final AppUserRepository users;
    private final GradeRepository grades;
    private final SubjectRepository subjects;
    private final QuestionVersionRepository questionVersions;
    private final TopicRepository topics;
    private final QuestionBankService questions;
    private final VocabularyAdminService words;
    private final FactAdminService facts;
    private final QuizGameAdminService games;
    private final QuizGameRepository gameRepository;

    /** Подключает редакционные сервисы; конструктор не пишет данные и не создаёт аккаунты. */
    public QuizStarterContentService(ObjectMapper mapper, AppUserRepository users, GradeRepository grades,
            SubjectRepository subjects, QuestionVersionRepository questionVersions, TopicRepository topics, QuestionBankService questions,
            VocabularyAdminService words, FactAdminService facts, QuizGameAdminService games, QuizGameRepository gameRepository) {
        this.mapper=mapper; this.users=users; this.grades=grades; this.subjects=subjects; this.topics=topics;
        this.questionVersions=questionVersions; this.questions=questions; this.words=words; this.facts=facts; this.games=games; this.gameRepository=gameRepository;
    }

    /**
     * Публикует четыре игры одной транзакцией от текущего ADMIN и явно указанного ADMIN-проверяющего.
     * Самопроверка подчиняется принятому damulab.quiz.strict-self-review через редакционные сервисы.
     * Повтор/коллизия кодов отклоняется без изменения прежних редакций; ошибка откатывает весь пакет.
     * Вызов допускается только после редакционной проверки ресурса; HTTP/startup-входа у метода нет.
     */
    @Transactional
    public List<String> install(String reviewerEmail) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if (authentication==null) throw new ContentValidationException("Для загрузки нужен автор ADMIN");
        var author=admin(authentication.getName()); var reviewer=admin(reviewerEmail);
        // Любая коллизия стабильных кодов откатывает пакет целиком; существующие редакции не перезаписываются.
        for (String code:CODES) if (gameRepository.findByCode(code).isPresent())
            throw new ContentValidationException("Стартовый код уже существует: "+code);
        JsonNode data=read("quiz/starter-v1.json");
        var grade=grades.findAllByOrderByGradeNoAsc().stream().filter(g->g.getGradeNo()==data.path("gradeNo").asInt()).findFirst()
                .orElseThrow(()->new ContentValidationException("Нет класса стартового пакета"));
        var en=wordSet(data,"en",CODES.get(0),grade,author,reviewer);
        var kk=wordSet(data,"kk",CODES.get(1),grade,author,reviewer);
        var math=subject(data,grade,author,reviewer);
        var fact=factSet(data,grade,author,reviewer);
        publish(en,author,reviewer); publish(kk,author,reviewer); publish(math,author,reviewer); publish(fact,author,reviewer);
        return CODES;
    }

    /**
     * Явно публикует две проверенные игры о животных через контракт двух ADMIN.
     * Не меняет существующие банки, аккаунты, комнаты или флаг; коллизия откатывает весь пакет.
     * Редакционный протокол RU/KK и первоисточники: feature/solo-vocabulary-quiz/ANIMAL_CONTENT.md.
     */
    @Transactional
    public List<String> installAnimalGames(String reviewerEmail) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if (authentication==null) throw new ContentValidationException("Для загрузки нужен автор ADMIN");
        var author=admin(authentication.getName()); var reviewer=admin(reviewerEmail);
        for (String code:ANIMAL_CODES) if (gameRepository.findByCode(code).isPresent())
            throw new ContentValidationException("Код игры о животных уже существует: "+code);
        JsonNode data=read("quiz/animal-facts-v1.json");
        var grade=grades.findAllByOrderByGradeNoAsc().stream().filter(g->g.getGradeNo()==data.path("gradeNo").asInt()).findFirst()
                .orElseThrow(()->new ContentValidationException("Нет класса пакета о животных"));
        for (var bank:data.path("games")) {
            String code=bank.path("code").asText();
            if (!ANIMAL_CODES.contains(code)) throw new ContentValidationException("Неизвестный код пакета о животных");
            var set=publishFactSet(bank.path("facts"),code,bank.path("titleRu").asText(),bank.path("titleKk").asText(),
                    data.path("sourcePolicy").asText(),grade,author,reviewer);
            var game=base(data,code,QuizGameType.TRUE_FALSE,grade,bank.path("titleRu").asText(),bank.path("titleKk").asText(),
                    bank.path("descriptionRu").asText(),bank.path("descriptionKk").asText(),"facts");
            game.setMechanicLabelRu("Верю / не верю"); game.setMechanicLabelKk("Сенемін / сенбеймін"); game.setFactSetId(set.getId());
            game.setMaxQuestionCount(bank.path("facts").size());
            // Объяснение сохраняется в обычном снимке и выдаётся только после FINISHED; правила движка не меняются.
            publish(game,author,reviewer);
        }
        return ANIMAL_CODES;
    }

    private AppUser admin(String email) {
        var user=users.findByEmailIgnoreCase(email).orElseThrow(()->new ContentValidationException("Администратор не найден"));
        if (!user.isEnabled() || user.getRoles().stream().noneMatch(r->r.getCode()==RoleCode.ADMIN))
            throw new ContentValidationException("Нужен действующий ADMIN");
        return user;
    }
    private JsonNode read(String resource) {
        try (var stream=new ClassPathResource(resource).getInputStream()) { return mapper.readTree(stream); }
        catch (IOException ex) { throw new IllegalStateException("Не удалось прочитать проверенный стартовый пакет",ex); }
    }
    private QuizGameForm base(JsonNode data,String code,QuizGameType type,Grade grade,String ru,String kk,String descriptionRu,String descriptionKk,String style) {
        var f=new QuizGameForm(); f.setCode(code); f.setGameType(type); f.setTitleRu(ru); f.setTitleKk(kk);
        f.setDescriptionRu(descriptionRu); f.setDescriptionKk(descriptionKk); f.setCardStyle(style);
        f.setAudiencePolicy(AudiencePolicy.FIXED_GRADES); f.setGradeIds(List.of(grade.getId()));
        f.setDefaultQuestionCount(data.path("count").asInt()); f.setMinQuestionCount(f.getDefaultQuestionCount()); f.setMaxQuestionCount(f.getDefaultQuestionCount());
        f.setDefaultRoundSeconds(data.path("seconds").asInt()); f.setMinRoundSeconds(f.getDefaultRoundSeconds()); f.setMaxRoundSeconds(f.getDefaultRoundSeconds());
        // В фактовых играх ведущий выбирает длительность и число утверждений перед стартом.
        // Верхнюю границу вопросов вызывающий метод уточняет по размеру опубликованного банка.
        if (type == QuizGameType.TRUE_FALSE) {
            f.setQuestionCountEditable(true); f.setRoundSecondsEditable(true);
            f.setMinQuestionCount(1); f.setMinRoundSeconds(5); f.setMaxRoundSeconds(120);
        }
        f.setMaxPlayers(4); return f;
    }
    private QuizGameForm wordSet(JsonNode data,String language,String code,Grade grade,AppUser author,AppUser reviewer) {
        boolean english=language.equals("en");
        String ru=(english?"Английский":"Казахский")+": числа 1–8";
        String kk=(english?"Ағылшын тілі":"Қазақ тілі")+": 1–8 сандары";
        var f=new VocabularySetForm(); f.setCode(code); f.setSourceLanguage(language); f.setTargetLanguage("ru"); f.setTitleRu(ru); f.setTitleKk(kk);
        f.setSourceReference(english?"Collins English Dictionary: number / In other languages":"Татьяна Валяева: https://www.kaz-tili.kz/chislit01.htm");
        var set=words.createSet(f,author.getId()); words.submitSet(set.getId(),author.getId()); words.approveSet(set.getId(),reviewer.getId()); words.publishSet(set.getId(),author.getId());
        for (var row:data.path("words")) {
            var item=new VocabularyItemForm(); item.setSetId(set.getId()); item.setSourceText(row.path(language).asText()); item.setPrimaryAnswer(row.path("ru").asText());
            item.setAlternativeAnswers(String.join("\n",mapper.convertValue(row.path("alternatives"),new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){})));
            item.setPartOfSpeech("числительное"); item.setMeaningNoteRu("Количество при счёте: "+row.path("number").asInt()+"; не порядковое числительное.");
            item.setMeaningNoteKk("Санау кезіндегі сан: "+row.path("number").asInt()+"; реттік сан емес.");
            item.setSourceReference(row.path(english?"enSource":"kkSource").asText()); item.setGradeIds(List.of(grade.getId()));
            var value=words.createItem(item,author.getId()); words.submitItem(value.getId(),author.getId()); words.approveItem(value.getId(),reviewer.getId()); words.publishItem(value.getId(),author.getId());
        }
        var game=base(data,code,QuizGameType.WORD_TRANSLATION,grade,ru,kk,"5 класс, начальный уровень: переведите количественное числительное на русский. Один ответ из четырёх.","5-сынып, бастапқы деңгей: есептік сан есімді орыс тіліне аударыңыз. Төрт жауаптың бірін таңдаңыз.",english?"english":"kazakh");
        game.setMechanicLabelRu(language.toUpperCase(Locale.ROOT)+" → RU"); game.setMechanicLabelKk(language.toUpperCase(Locale.ROOT)+" → RU");
        game.setVocabularySetId(set.getId()); game.setSourceLanguage(language); game.setTargetLanguage("ru"); return game;
    }
    private QuizGameForm factSet(JsonNode data,Grade grade,AppUser author,AppUser reviewer) {
        var set=publishFactSet(data.path("facts"),CODES.get(3),"Солнечная система","Күн жүйесі",
                "NASA Science; проверены 2026-10-10; только устойчивые базовые факты, без меняющихся чисел спутников.",grade,author,reviewer);
        var game=base(data,CODES.get(3),QuizGameType.TRUE_FALSE,grade,"Солнечная система: верю / не верю","Күн жүйесі: сенемін / сенбеймін","5 класс: проверьте базовые факты о Солнце, планетах и Луне. Объяснение и источник — после игры.","5-сынып: Күн, ғаламшарлар және Ай туралы негізгі деректерді тексеріңіз. Түсіндірме мен дереккөз ойыннан кейін беріледі.","facts");
        game.setMechanicLabelRu("Верю / не верю"); game.setMechanicLabelKk("Сенемін / сенбеймін"); game.setFactSetId(set.getId());
        game.setMaxQuestionCount(data.path("facts").size()); return game;
    }
    private FactSet publishFactSet(JsonNode rows,String code,String ru,String kk,String policy,Grade grade,AppUser author,AppUser reviewer) {
        var f=new FactSetForm(); f.setCode(code); f.setTitleRu(ru); f.setTitleKk(kk); f.setSourcePolicy(policy);
        var set=facts.createSet(f,author.getId());
        facts.transitionSet(set.getId(),EditorialStatus.NEEDS_REVIEW,author.getId()); facts.transitionSet(set.getId(),EditorialStatus.APPROVED,reviewer.getId()); facts.transitionSet(set.getId(),EditorialStatus.PUBLISHED,author.getId());
        for (var row:rows) {
            var item=new FactStatementForm(); item.setSetId(set.getId()); item.setTextRu(row.path("ru").asText()); item.setTextKk(row.path("kk").asText());
            item.setTruthValue(row.path("truth").booleanValue()); item.setExplanationRu(row.path("explanationRu").asText()); item.setExplanationKk(row.path("explanationKk").asText());
            item.setSourceReference(row.path("source").asText()); item.setGradeIds(List.of(grade.getId()));
            var value=facts.createStatement(item,author.getId()); facts.submit(value.getId(),author.getId()); facts.approve(value.getId(),reviewer.getId()); facts.publish(value.getId(),author.getId());
        }
        return set;
    }
    private QuizGameForm subject(JsonNode data,Grade grade,AppUser author,AppUser reviewer) {
        var subject=subjects.findByCodeIgnoreCase("math")
                .orElseThrow(()->new ContentValidationException("Нет существующего предмета Математика"));
        // Config не фильтрует тему: отказываем при примеси чужих публикаций вместо изменения справочника
        // или молчаливого объявления непроверенного банка частью проверенного starter.
        boolean mixedBank=questionVersions.findPublishedForQuizSource(subject.getId()).stream()
                .anyMatch(v->v.getType()==QuestionType.SCQ && v.getDifficulty()==1
                        && QuizContentEligibility.subjectAudience(v,Set.of(grade.getId()))
                        && QuizContentEligibility.subjectComplete(v));
        if (mixedBank) throw new ContentValidationException("Для стартового пакета требуется проверка существующих вопросов Математики: 5 класс, SCQ, сложность 1");
        var topic=topics.findByScopeAndCode(subject.getId(),grade.getId(),null,"starter-sums-v1")
                .orElseGet(()->topics.save(new Topic(subject,grade,null,"starter-sums-v1","Сложение натуральных чисел","Натурал сандарды қосу")));
        for (var row:data.path("sums")) {
            var f=new QuestionForm(); f.setSubjectId(subject.getId()); f.setTopicIds(List.of(topic.getId())); f.setGradeIds(List.of(grade.getId()));
            f.setType(QuestionType.SCQ); f.setDifficulty(1); f.setBodyRu(row.path("ru").asText()); f.setBodyKk(row.path("kk").asText());
            f.setExplanationRu(row.path("explanationRu").asText()); f.setExplanationKk(row.path("explanationKk").asText()); f.setSource(row.path("source").asText());
            var options=new ArrayList<ChoiceOptionForm>(); int index=0;
            for (var option:row.path("options")) options.add(new ChoiceOptionForm(String.valueOf((char)('A'+index++)),option.asText(),option.asText(),option.asInt()==row.path("answer").asInt()));
            f.setOptions(options);
            var value=questions.createQuestion(f);
            // Старый банк вопросов берёт автора из SecurityContext; для реального audit проверяющего временно меняется только текущий поток.
            var original=SecurityContextHolder.getContext().getAuthentication();
            try {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(reviewer.getEmail(),null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
                questions.approve(value.id());
            } finally { SecurityContextHolder.getContext().setAuthentication(original); }
            questions.publish(value.id());
        }
        var game=base(data,CODES.get(2),QuizGameType.SUBJECT_QUIZ,grade,"Математика: сложение до 1000","Математика: 1000-ға дейін қосу","5 класс: повторение сложения натуральных чисел с переносом. Выберите один ответ.","5-сынып: натурал сандарды тасымалдап қосуды қайталау. Бір жауапты таңдаңыз.","math");
        game.setMechanicLabelRu("Один правильный ответ"); game.setMechanicLabelKk("Бір дұрыс жауап");
        game.setSubjectId(subject.getId()); game.setDifficulty(1); game.setQuestionTypes(Set.of(QuestionType.SCQ)); return game;
    }
    private void publish(QuizGameForm form,AppUser author,AppUser reviewer) {
        var game=games.create(form,author.getId());
        games.transition(game.getId(),game.getAdminRevision(),"submit",author.getId());
        games.transition(game.getId(),game.getAdminRevision(),"approve",reviewer.getId());
        games.publish(game.getId(),game.getAdminRevision(),false,author.getId());
    }
}
