package kz.damulab.lectures;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.ai.AiLectureGenerationRequest;
import kz.damulab.ai.AiLectureGenerationResult;
import kz.damulab.ai.AiProvider;
import kz.damulab.content.Topic;
import kz.damulab.content.TopicRepository;

/**
 * Связывает административную форму с AI-маршрутом. Клиент передаёт только topicId
 * и подсказку методиста; предмет, класс и двуязычные названия всегда берутся из БД.
 */
@Service
public class LectureAiGenerationService {

    private final TopicRepository topics;
    private final AiProvider aiProvider;

    public LectureAiGenerationService(TopicRepository topics, AiProvider aiProvider) {
        this.topics = topics;
        this.aiProvider = aiProvider;
    }

    /** Генерирует черновик для редактора, но не сохраняет и не публикует лекцию автоматически. */
    @Transactional(readOnly = true)
    public AiLectureGenerationResult generate(LectureAiGenerationForm form) {
        Topic topic = topics.findById(form.getTopicId())
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new LectureException("topic_not_found"));
        return aiProvider.generateLecture(new AiLectureGenerationRequest(
                topic.getSubject().getTitleRu(),
                topic.getSubject().getTitleKk(),
                topic.getGrade().getGradeNo(),
                topic.getGrade().getTitleRu(),
                topic.getGrade().getTitleKk(),
                topic.getTitleRu(),
                topic.getTitleKk(),
                normalize(form.getMethodistInstruction())
        ));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "Без дополнительных требований" : value.trim();
    }
}
