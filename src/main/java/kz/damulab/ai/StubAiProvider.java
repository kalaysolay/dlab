package kz.damulab.ai;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import kz.damulab.questions.FillMatchMode;
import kz.damulab.questions.QuestionType;

@Component
public class StubAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(StubAiProvider.class);

    static final String FAILURE_TOKEN = "__FAIL_PROVIDER__";

    private static final String STUB_MINI_LECTURE_HINT_RU = """
            Это ЗАГЛУШКА: внешний LLM не вызывается. Чтобы получить реальную мини-лекцию (JSON → HTML по промпту приложения), задайте в окружении:
            AI_PROVIDER=openai
            AI_REAL_PROVIDERS_ENABLED=true
            OPENAI_API_KEY=sk-…
            (опционально OPENAI_MINI_LECTURE_MODEL=gpt-4o), перезапустите сервер и снова нажмите «Сгенерировать».

            Ниже — сжатая выжимка из полей формы только для проверки UI.""";

    private static final String STUB_MINI_LECTURE_HINT_KK = """
            Бұл STUB: сыртқы LLM шақырылмайды. Нақты мини-лекция үшін ортада:
            AI_PROVIDER=openai
            AI_REAL_PROVIDERS_ENABLED=true
            OPENAI_API_KEY=sk-…
            орнатып, серверді қайта іске қосыңыз.

            Төменде — UI тексеруі үшін форма өрістерінің қысқаша мазмұны.""";

    private AiQuestionGenerationRequest lastRequest;

    @Override
    public AiQuestionGenerationResult generateQuestions(AiQuestionGenerationRequest request) {
        lastRequest = request;
        if (request.methodistInstruction() != null && request.methodistInstruction().contains(FAILURE_TOKEN)) {
            throw new AiProviderException("stub_provider_failure", "Stub provider failure requested");
        }
        List<AiGeneratedQuestionDraft> drafts = new ArrayList<>();
        for (int index = 1; index <= request.count(); index++) {
            drafts.add(draft(request, index));
        }
        return new AiQuestionGenerationResult("stub", "stub-ai-content-factory-v1", drafts);
    }

    @Override
    public AiMiniLectureResult generateMiniLecture(MiniLectureGenerationRequest request) {
        log.info(
                "StubAiProvider.generateMiniLecture: предмет RU (первые 80 симв.)='{}', класс={}, вопрос RU (первые 80)='{}'",
                preview(request.subjectRu(), 80),
                request.gradeNo(),
                preview(request.questionRu(), 80)
        );
        return MiniLectureHtmlComposer.toResult(stubStructuredPayload(request));
    }

    private static String preview(String text, int max) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        String t = text.trim().replace('\n', ' ');
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private MiniLectureStructuredPayload stubStructuredPayload(MiniLectureGenerationRequest r) {
        String subRu = trimOrDash(r.subjectRu());
        String subKk = trimOrDash(r.subjectKk());
        String qRu = trimOrDash(r.questionRu());
        String qKk = trimOrDash(r.questionKk());
        String gradeLineRu = "Класс из формы: " + r.gradeNo() + " — " + trimOrDash(r.gradeTitleRu());
        String gradeLineKk = "Формадағы сынып: " + r.gradeNo() + " — " + trimOrDash(r.gradeTitleKk());
        MiniLectureLangBlock ru = new MiniLectureLangBlock(
                "Мини-лекция (stub, без внешнего AI)",
                STUB_MINI_LECTURE_HINT_RU.trim()
                        + "\n\nПредмет: " + subRu + ".\n" + gradeLineRu + ".\nВопрос (RU): " + qRu,
                "Контекст для проверки: условие и варианты уже переданы в промпт при реальном провайдере; здесь только echo.",
                "Верный ответ (из формы): " + trimOrDash(r.correctAnswerRu()),
                "Пример разбора в stub не генерируется — его заменит ответ модели после включения OpenAI/DeepSeek.",
                "Сохраните вопрос и повторите генерацию с настроенным API-ключом."
        );
        MiniLectureLangBlock kz = new MiniLectureLangBlock(
                "Мини-лекция (stub)",
                STUB_MINI_LECTURE_HINT_KK.trim()
                        + "\n\nПән: " + subKk + ".\n" + gradeLineKk + ".\nСұрақ (KK): " + qKk,
                "Тексеру контексті: нақты провайдерде шарт пен нұсқалар промптқа жіберіледі.",
                "Дұрыс жауап (формадан): " + trimOrDash(r.correctAnswerKk()),
                "Stub режимінде ұқсас мысал шығарылмайды — OpenAI/DeepSeek қосқанда модель толтырады.",
                "API кілтін баптап, қайта генерациялаңыз."
        );
        return new MiniLectureStructuredPayload(ru, kz);
    }

    private static String trimOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }

    public AiQuestionGenerationRequest getLastRequest() {
        return lastRequest;
    }

    private AiGeneratedQuestionDraft draft(AiQuestionGenerationRequest request, int index) {
        String topicRu = request.topicTitleRu();
        String topicKk = request.topicTitleKk();
        int score = index % 3 == 0 ? 64 : 88;
        QuestionType type = request.questionType();
        return new AiGeneratedQuestionDraft(
                type,
                request.difficulty(),
                bodyRu(type, topicRu, index),
                bodyKk(type, topicKk, index),
                "Разделите число на 10, потому что 10% равно одной десятой.",
                "10% бір ондыққа тең, сондықтан санды 10-ға бөліңіз.",
                "AI stub: " + topicRu,
                choiceOptions(type, index),
                matchingPairs(type),
                fillAnswers(type, index),
                score,
                score >= 80 ? "Stub quality check passed" : "Draft needs methodist review",
                score >= 80 ? List.of() : List.of("low_quality_score")
        );
    }

    private String bodyRu(QuestionType type, String topicRu, int index) {
        return switch (type) {
            case SCQ, MCQ -> "AI draft " + index + ": найдите 10% от " + (100 + index * 20) + " по теме " + topicRu;
            case MATCHING -> "AI draft " + index + ": сопоставьте проценты и десятичные дроби по теме " + topicRu;
            case FILL_IN -> "AI draft " + index + ": 10% от " + (100 + index * 20) + " равно [[1]]";
        };
    }

    private String bodyKk(QuestionType type, String topicKk, int index) {
        return switch (type) {
            case SCQ, MCQ -> "AI draft " + index + ": " + topicKk + " тақырыбы бойынша " + (100 + index * 20) + " санының 10 пайызын табыңыз";
            case MATCHING -> "AI draft " + index + ": " + topicKk + " тақырыбы бойынша пайыздар мен ондық бөлшектерді сәйкестендіріңіз";
            case FILL_IN -> "AI draft " + index + ": " + (100 + index * 20) + " санының 10 пайызы [[1]]";
        };
    }

    private List<AiGeneratedChoiceOption> choiceOptions(QuestionType type, int index) {
        if (type != QuestionType.SCQ && type != QuestionType.MCQ) {
            return List.of();
        }
        boolean multi = type == QuestionType.MCQ;
        return List.of(
                new AiGeneratedChoiceOption("A", String.valueOf(10 + index * 2), String.valueOf(10 + index * 2), true),
                new AiGeneratedChoiceOption("B", String.valueOf(5 + index), String.valueOf(5 + index), multi),
                new AiGeneratedChoiceOption("C", String.valueOf(20 + index), String.valueOf(20 + index), false)
        );
    }

    private List<AiGeneratedMatchingPair> matchingPairs(QuestionType type) {
        if (type != QuestionType.MATCHING) {
            return List.of();
        }
        return List.of(
                new AiGeneratedMatchingPair("50%", "50%", "0.5", "0.5"),
                new AiGeneratedMatchingPair("25%", "25%", "0.25", "0.25")
        );
    }

    private List<AiGeneratedFillAnswer> fillAnswers(QuestionType type, int index) {
        if (type != QuestionType.FILL_IN) {
            return List.of();
        }
        return List.of(new AiGeneratedFillAnswer(
                "[[1]]",
                String.valueOf(10 + index * 2),
                FillMatchMode.NUMERIC_TOLERANCE,
                java.math.BigDecimal.valueOf(0.01)
        ));
    }
}
