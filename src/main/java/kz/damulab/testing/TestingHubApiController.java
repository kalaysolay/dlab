package kz.damulab.testing;

import java.net.URI;
import java.security.Principal;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test-sessions")
public class TestingHubApiController {

    private final TestingHubService testingHub;

    public TestingHubApiController(TestingHubService testingHub) {
        this.testingHub = testingHub;
    }

    @PostMapping
    ResponseEntity<TestSessionResponse> start(Principal principal, @Valid @RequestBody StartTestSessionRequest request) {
        TestSessionResponse response = testingHub.startSession(principal.getName(), request);
        return ResponseEntity
                .created(URI.create("/api/test-sessions/" + response.id()))
                .body(response);
    }

    @GetMapping("/{sessionId}")
    TestSessionResponse session(Principal principal, @PathVariable Long sessionId) {
        return testingHub.getSession(principal.getName(), sessionId);
    }

    @PatchMapping("/{sessionId}/answers")
    TestSessionResponse answer(
            Principal principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody SubmitAnswerRequest request
    ) {
        return testingHub.submitAnswer(principal.getName(), sessionId, request);
    }

    @PostMapping("/{sessionId}/finish")
    TestResultResponse finish(Principal principal, @PathVariable Long sessionId) {
        return testingHub.finishSession(principal.getName(), sessionId);
    }

    @GetMapping("/{sessionId}/result")
    TestResultResponse result(Principal principal, @PathVariable Long sessionId) {
        return testingHub.getResult(principal.getName(), sessionId);
    }
}
