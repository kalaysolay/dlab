package kz.damulab.testing;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import kz.damulab.content.ContentGraphService;
import kz.damulab.content.ContentReferencesResponse;

@RestController
public class TestingReferenceApiController {

    private final ContentGraphService contentGraph;

    public TestingReferenceApiController(ContentGraphService contentGraph) {
        this.contentGraph = contentGraph;
    }

    @GetMapping("/api/tests/types")
    List<TestTypeResponse> types() {
        return Arrays.stream(TestType.values())
                .map(type -> new TestTypeResponse(type.name(), type.getTitleRu()))
                .toList();
    }

    @GetMapping("/api/tests/filters")
    ContentReferencesResponse filters() {
        return contentGraph.references(null, null);
    }
}
