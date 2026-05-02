package kz.damulab.lectures;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class StudentLecturePageController {

    private final LectureService lectureService;

    public StudentLecturePageController(LectureService lectureService) {
        this.lectureService = lectureService;
    }

    @GetMapping("/student/lectures")
    String lectures(Model model) {
        model.addAttribute("lectures", lectureService.listPublishedLectures());
        model.addAttribute("adminPreview", false);
        return "student/lectures";
    }

    @GetMapping("/student/lectures/{id}")
    String lecture(@PathVariable Long id, Model model) {
        model.addAttribute("lecture", lectureService.getPublishedLecture(id));
        model.addAttribute("adminPreview", false);
        return "student/lecture";
    }
}
