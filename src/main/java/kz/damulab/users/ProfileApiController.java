package kz.damulab.users;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileApiController {

    private final ProfileService profileService;

    public ProfileApiController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/api/student/profile")
    StudentProfileResponse studentProfile(Principal principal) {
        return profileService.getStudentProfile(principal.getName());
    }

    @PatchMapping("/api/student/profile")
    StudentProfileResponse updateStudentProfile(
            Principal principal,
            @Valid @RequestBody StudentProfileForm form
    ) {
        return profileService.updateStudentProfile(principal.getName(), form);
    }

    @GetMapping("/api/parent/profile")
    ParentProfileResponse parentProfile(Principal principal) {
        return profileService.getParentProfile(principal.getName());
    }

    @PatchMapping("/api/parent/profile")
    ParentProfileResponse updateParentProfile(
            Principal principal,
            @Valid @RequestBody ParentProfileForm form
    ) {
        return profileService.updateParentProfile(principal.getName(), form);
    }
}
