package kz.damulab.parentlink;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ParentLinkApiController {

    private final ParentLinkService parentLinkService;

    public ParentLinkApiController(ParentLinkService parentLinkService) {
        this.parentLinkService = parentLinkService;
    }

    @GetMapping("/api/parent/children")
    List<ChildResponse> children(Principal principal) {
        return parentLinkService.listChildren(principal.getName());
    }

    @PostMapping("/api/parent/children")
    @ResponseStatus(HttpStatus.CREATED)
    ChildResponse createChild(Principal principal, @Valid @RequestBody CreateChildForm form) {
        return parentLinkService.createChild(principal.getName(), form);
    }

    @GetMapping("/api/parent/children/{studentId}")
    ChildResponse child(Principal principal, @PathVariable Long studentId) {
        return parentLinkService.getChild(principal.getName(), studentId);
    }

    @PostMapping("/api/parent/link-codes/{code}/attach")
    ChildResponse attachByCode(Principal principal, @PathVariable String code) {
        return parentLinkService.attachChildByCode(principal.getName(), code);
    }

    @PostMapping("/api/parent/children/{studentId}/link-code")
    LinkCodeResponse createOwnedChildLinkCode(Principal principal, @PathVariable Long studentId) {
        return parentLinkService.createOwnedChildLinkCode(principal.getName(), studentId);
    }

    @DeleteMapping("/api/parent/children/{studentId}/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlink(Principal principal, @PathVariable Long studentId) {
        parentLinkService.unlinkChild(principal.getName(), studentId);
    }

    @PostMapping("/api/student/link-codes")
    LinkCodeResponse createStudentLinkCode(Principal principal) {
        return parentLinkService.createStudentLinkCode(principal.getName());
    }
}
