package kz.damulab.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
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

@Component
public class DemoUserSeeder implements ApplicationRunner {

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final StudentProfileRepository studentProfiles;
    private final ParentProfileRepository parentProfiles;
    private final PasswordEncoder passwordEncoder;

    public DemoUserSeeder(
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing("admin@damulab.kz", "Администратор", RoleCode.ADMIN);
        createIfMissing("student@damulab.kz", "Demo Student", RoleCode.STUDENT);
        createIfMissing("parent@damulab.kz", "Demo Parent", RoleCode.PARENT);
    }

    private void createIfMissing(String email, String fullName, RoleCode roleCode) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        Role role = roles.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Missing role: " + roleCode));
        AppUser user = new AppUser(email, passwordEncoder.encode("password"), fullName, null);
        user.addRole(role);
        AppUser saved = users.save(user);
        if (roleCode == RoleCode.STUDENT) {
            studentProfiles.save(new StudentProfile(saved, 4, "ru"));
        }
        if (roleCode == RoleCode.PARENT) {
            parentProfiles.save(new ParentProfile(saved, null));
        }
    }
}
