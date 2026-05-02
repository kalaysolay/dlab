package kz.damulab.auth;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;
import kz.damulab.users.ParentProfile;
import kz.damulab.users.ParentProfileRepository;
import kz.damulab.users.Role;
import kz.damulab.users.RoleCode;
import kz.damulab.users.RoleRepository;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

@Service
public class RegistrationService {

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final StudentProfileRepository studentProfiles;
    private final ParentProfileRepository parentProfiles;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            AppUserRepository users,
            RoleRepository roles,
            StudentProfileRepository studentProfiles,
            ParentProfileRepository parentProfiles,
            PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.roles = roles;
        this.studentProfiles = studentProfiles;
        this.parentProfiles = parentProfiles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser register(RegisterForm form) {
        String email = normalizeEmail(form.getEmail());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }

        RoleCode roleCode = form.getRole() == null ? RoleCode.STUDENT : form.getRole();
        if (roleCode == RoleCode.ADMIN) {
            throw new IllegalArgumentException("Self-registration as ADMIN is not allowed");
        }

        Role role = roles.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Missing role: " + roleCode));
        AppUser user = new AppUser(
                email,
                passwordEncoder.encode(form.getPassword()),
                form.getFullName().trim(),
                blankToNull(form.getPhone())
        );
        user.addRole(role);
        AppUser saved = users.save(user);

        if (roleCode == RoleCode.STUDENT) {
            studentProfiles.save(new StudentProfile(saved, form.getGradeNo(), form.getPreferredLanguage()));
        } else if (roleCode == RoleCode.PARENT) {
            parentProfiles.save(new ParentProfile(saved, blankToNull(form.getPhone())));
        }
        return saved;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
