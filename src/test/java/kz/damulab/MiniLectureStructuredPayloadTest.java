package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import kz.damulab.ai.AiMiniLectureResult;
import kz.damulab.ai.MiniLectureHtmlComposer;
import kz.damulab.ai.MiniLectureJsonParser;
import kz.damulab.ai.MiniLectureStructuredPayload;

class MiniLectureStructuredPayloadTest {

    private static final String SAMPLE_JSON = """
            {
              "ru": {
                "title": "T",
                "theory": "A B",
                "question_analysis": "Q",
                "common_mistake": "M",
                "example_analysis": "E",
                "summary": "S"
              },
              "kz": {
                "title": "T2",
                "theory": "A2",
                "question_analysis": "Q2",
                "common_mistake": "M2",
                "example_analysis": "E2",
                "summary": "S2"
              }
            }
            """;

    @Test
    void parserBuildsPayload() {
        ObjectMapper mapper = new ObjectMapper();
        MiniLectureStructuredPayload payload = MiniLectureJsonParser.parse(mapper, SAMPLE_JSON);
        assertThat(payload.ru().title()).isEqualTo("T");
        assertThat(payload.kz().summary()).isEqualTo("S2");
    }

    @Test
    void composerProducesMiniLectureArticle() {
        ObjectMapper mapper = new ObjectMapper();
        MiniLectureStructuredPayload payload = MiniLectureJsonParser.parse(mapper, SAMPLE_JSON);
        AiMiniLectureResult html = MiniLectureHtmlComposer.toResult(payload);
        assertThat(html.contentRu()).contains("class=\"mini-lecture\"");
        assertThat(html.contentRu()).contains("Теория");
        assertThat(html.contentKk()).contains("class=\"mini-lecture\"");
    }
}
