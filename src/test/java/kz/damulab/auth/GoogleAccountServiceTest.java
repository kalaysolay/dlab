package kz.damulab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;
import kz.damulab.users.ParentProfileRepository;
import kz.damulab.users.PhoneNormalizer;
import kz.damulab.users.Role;
import kz.damulab.users.RoleCode;
import kz.damulab.users.RoleRepository;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

/** Проверяет безопасную привязку Google sub и создание локального ролевого профиля. */
@ExtendWith(MockitoExtension.class)
class GoogleAccountServiceTest {

    @Mock
    private AppUserRepository users;
    @Mock
    private RoleRepository roles;
    @Mock
    private StudentProfileRepository studentProfiles;
    @Mock
    private ParentProfileRepository parentProfiles;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PhoneNormalizer phoneNormalizer;

    private GoogleAccountService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAccountService(
                users, roles, studentProfiles, parentProfiles, passwordEncoder, phoneNormalizer
        );
    }

    @Test
    void linksExistingEmailAndCompletesEmailVerification() {
        AppUser existing = new AppUser("student@example.com", "hash", "Student", null);
        existing.requireEmailVerification();
        GoogleIdentity identity = new GoogleIdentity("google-sub-1", "student@example.com", "Google Name");
        when(users.findByGoogleSubject("google-sub-1")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(existing));
        when(users.save(existing)).thenReturn(existing);

        AppUser linked = service.findAndLinkExisting(identity).orElseThrow();

        assertThat(linked.getGoogleSubject()).isEqualTo("google-sub-1");
        assertThat(linked.isEnabled()).isTrue();
        assertThat(linked.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void createsStudentProfileForNewGoogleAccount() {
        GoogleIdentity identity = new GoogleIdentity("google-sub-2", "new@example.com", "New User");
        GoogleRegistrationForm form = new GoogleRegistrationForm();
        form.setFullName("New User");
        form.setRole(RoleCode.STUDENT);
        form.setGradeNo(4);
        form.setPhone("8 (701) 123-45-67");
        form.setPreferredLanguage("kk");

        when(users.findByGoogleSubject("google-sub-2")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(roles.findByCode(RoleCode.STUDENT)).thenReturn(Optional.of(new Role(RoleCode.STUDENT)));
        when(phoneNormalizer.normalize("8 (701) 123-45-67")).thenReturn("+77011234567");
        when(passwordEncoder.encode(any())).thenReturn("random-hash");
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser created = service.register(identity, form);

        assertThat(created.getEmail()).isEqualTo("new@example.com");
        assertThat(created.getGoogleSubject()).isEqualTo("google-sub-2");
        assertThat(created.getPhone()).isEqualTo("+77011234567");
        assertThat(created.getRoles()).extracting(Role::getCode).containsExactly(RoleCode.STUDENT);
        assertThat(created.getEmailVerifiedAt()).isNotNull();
        verify(studentProfiles).save(any(StudentProfile.class));
        verify(parentProfiles, never()).save(any());
    }

    @Test
    void rejectsAdminSelfRegistration() {
        GoogleIdentity identity = new GoogleIdentity("google-sub-3", "admin@example.com", "Admin");
        GoogleRegistrationForm form = new GoogleRegistrationForm();
        form.setFullName("Admin");
        form.setRole(RoleCode.ADMIN);
        when(users.findByGoogleSubject("google-sub-3")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(identity, form))
                .isInstanceOf(GoogleOAuthException.class)
                .hasMessageContaining("ADMIN");
    }
}
