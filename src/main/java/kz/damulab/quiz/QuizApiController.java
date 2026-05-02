package kz.damulab.quiz;

import java.net.URI;
import java.security.Principal;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz/rooms")
public class QuizApiController {

    private final QuizService quizService;

    public QuizApiController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping
    ResponseEntity<QuizRoomResponse> create(Principal principal, @Valid @RequestBody CreateQuizRoomRequest request) {
        QuizRoomResponse response = quizService.createRoom(principal.getName(), request);
        return ResponseEntity
                .created(URI.create("/api/quiz/rooms/" + response.code()))
                .body(response);
    }

    @GetMapping("/{code}")
    QuizRoomResponse room(Principal principal, @PathVariable String code) {
        return quizService.room(code, principal.getName());
    }

    @PostMapping("/{code}/join")
    QuizRoomResponse join(Principal principal, @PathVariable String code) {
        return quizService.join(code, principal.getName());
    }

    @PostMapping("/{code}/ready")
    QuizRoomResponse ready(Principal principal, @PathVariable String code) {
        return quizService.ready(code, principal.getName());
    }

    @PostMapping("/{code}/start")
    QuizRoomResponse start(Principal principal, @PathVariable String code) {
        return quizService.start(code, principal.getName());
    }

    @PostMapping("/{code}/answers")
    QuizRoomResponse answer(
            Principal principal,
            @PathVariable String code,
            @Valid @RequestBody SubmitQuizAnswerRequest request
    ) {
        return quizService.submitAnswer(code, principal.getName(), request);
    }

    @GetMapping("/{code}/results")
    QuizResultsResponse results(Principal principal, @PathVariable String code) {
        return quizService.results(code, principal.getName());
    }
}
