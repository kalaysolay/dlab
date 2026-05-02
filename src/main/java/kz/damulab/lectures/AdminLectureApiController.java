package kz.damulab.lectures;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/lectures")
public class AdminLectureApiController {

    private final LectureService lectureService;

    public AdminLectureApiController(LectureService lectureService) {
        this.lectureService = lectureService;
    }

    @GetMapping
    List<LectureResponse> lectures(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) LectureStatus status,
            @RequestParam(required = false) String query
    ) {
        return lectureService.listLectures(topicId, status, query);
    }

    @GetMapping("/{id}")
    LectureResponse lecture(@PathVariable Long id) {
        return lectureService.getLecture(id);
    }

    @PostMapping
    ResponseEntity<LectureResponse> createLecture(@Valid @RequestBody LectureForm form) {
        LectureResponse created = lectureService.createLecture(form);
        return ResponseEntity.created(URI.create("/api/admin/lectures/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    LectureResponse updateLecture(@PathVariable Long id, @Valid @RequestBody LectureForm form) {
        return lectureService.updateLecture(id, form);
    }

    @PostMapping("/{id}/publish")
    LectureResponse publish(@PathVariable Long id) {
        return lectureService.publish(id);
    }

    @PostMapping("/{id}/archive")
    LectureResponse archive(@PathVariable Long id) {
        return lectureService.archive(id);
    }
}
