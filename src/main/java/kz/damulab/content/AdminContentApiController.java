package kz.damulab.content;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminContentApiController {

    private final ContentGraphService contentGraph;

    public AdminContentApiController(ContentGraphService contentGraph) {
        this.contentGraph = contentGraph;
    }

    @GetMapping("/subjects")
    List<ReferenceOption> subjects() {
        return contentGraph.listSubjects();
    }

    @GetMapping("/grades")
    List<GradeOption> grades() {
        return contentGraph.listGrades();
    }

    @GetMapping("/topics")
    List<TopicResponse> topics(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId
    ) {
        return contentGraph.listTopics(subjectId, gradeId);
    }

    @GetMapping("/topics/tree")
    List<TopicTreeNode> topicTree(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId
    ) {
        return contentGraph.topicTree(subjectId, gradeId);
    }

    @PostMapping("/topics")
    ResponseEntity<TopicResponse> createTopic(@Valid @RequestBody TopicForm form) {
        TopicResponse created = contentGraph.createTopic(form);
        return ResponseEntity.created(URI.create("/api/admin/topics/" + created.id())).body(created);
    }

    @PatchMapping("/topics/{id}")
    TopicResponse updateTopic(@PathVariable Long id, @Valid @RequestBody TopicForm form) {
        return contentGraph.updateTopic(id, form);
    }

    @DeleteMapping("/topics/{id}")
    ResponseEntity<Void> deleteTopic(@PathVariable Long id) {
        contentGraph.deleteTopic(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/topics/{id}/skills")
    List<AtomicSkillResponse> topicSkills(@PathVariable Long id) {
        return contentGraph.listSkills(id);
    }

    @PostMapping("/topics/{id}/skills")
    ResponseEntity<AtomicSkillResponse> createSkill(
            @PathVariable Long id,
            @Valid @RequestBody AtomicSkillForm form
    ) {
        AtomicSkillResponse created = contentGraph.createSkill(id, form);
        return ResponseEntity.created(URI.create("/api/admin/skills/" + created.id())).body(created);
    }

    @PatchMapping("/skills/{id}")
    AtomicSkillResponse updateSkill(@PathVariable Long id, @Valid @RequestBody AtomicSkillForm form) {
        return contentGraph.updateSkill(id, form);
    }

    @DeleteMapping("/skills/{id}")
    ResponseEntity<Void> deleteSkill(@PathVariable Long id) {
        contentGraph.deleteSkill(id);
        return ResponseEntity.noContent().build();
    }
}
