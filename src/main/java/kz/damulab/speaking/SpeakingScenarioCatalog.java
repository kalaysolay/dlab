package kz.damulab.speaking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Загружает только проверенные карточки из ресурсов; браузер не может прислать свой системный сценарий. */
@Component
public class SpeakingScenarioCatalog {
    private final List<SpeakingScenario> scenarios;
    public SpeakingScenarioCatalog(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("speaking/scenarios.json").getInputStream()) {
            scenarios = List.copyOf(mapper.readValue(stream, new TypeReference<List<SpeakingScenario>>() {}));
        }
        if (scenarios.size() != 5 || scenarios.stream().map(SpeakingScenario::code).distinct().count() != scenarios.size()
                || scenarios.stream().anyMatch(s -> s.version() < 1 || s.questions().size() != 8
                    || !s.firstQuestions().keySet().containsAll(List.of("A2", "B1", "B2")))) {
            throw new IllegalStateException("Invalid speaking scenario catalog");
        }
    }
    public List<SpeakingScenario> list() { return scenarios; }
    public SpeakingScenario find(String code) {
        return scenarios.stream().filter(s -> s.code().equals(code)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "speaking_unknown_scenario"));
    }
}
