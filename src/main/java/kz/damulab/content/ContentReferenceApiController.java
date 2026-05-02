package kz.damulab.content;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentReferenceApiController {

    private final ContentGraphService contentGraph;

    public ContentReferenceApiController(ContentGraphService contentGraph) {
        this.contentGraph = contentGraph;
    }

    @GetMapping("/api/content/references")
    ContentReferencesResponse references(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId
    ) {
        return contentGraph.references(subjectId, gradeId);
    }
}
