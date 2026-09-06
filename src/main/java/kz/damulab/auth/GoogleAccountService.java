package kz.damulab.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

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

/**
 * Привязывает проверенный Google OIDC identity к локальному пользователю.
 * Сначала ищет по стабильному {@code sub}; email используется только для одноразовой привязки
 * уже существующего аккаунта и никогда не заменяет внешний идентификатор.
 */
@Service
public class GoogleAccountService {

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final StudentProfileRepository studentProfiles;
    private final ParentProfileRepository parentProfiles;
    private final PasswordEncoder passwordEncoder;

    public GoogleAccountService(
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

    /**
     * Возвращает существующий аккаунт и при первом Google-входе связывает его по подтверждённому email.
     * Подтверждённый Google email одновременно завершает локальную email-верификацию.
     */
    @Transactional
    public Optional<AppUser> findAndLinkExisting(GoogleIdentity identity) {
        Optional<AppUser> bySubject = users.findByGoogleSubject(identity.subject());
        if (bySubject.isPresent()) {
            return bySubject;
        }

        Optional<AppUser> byEmail = users.findByEmailIgnoreCase(identity.email());
        if (byEmail.isEmpty()) {
            return Optional.empty();
        }

        AppUser user = byEmail.get();
        try {
            user.linkGoogleSubject(identity.subject());
        } catch (IllegalStateException ex) {
            throw new GoogleOAuthException("Local account is linked to another Google identity");
        }
        if (!user.isEnabled()) {
            user.confirmEmail(OffsetDateTime.now());
        }
        return Optional.of(users.save(user));
    }

    /** Создаёт локальный STUDENT/PARENT профиль после выбора роли на завершающем экране. */
    @Transactional
    public AppUser register(GoogleIdentity identity, GoogleRegistrationForm form) {
        Optional<AppUser> existing = findAndLinkExisting(identity);
        if (existing.isPresent()) {
            return existing.get();
        }

        RoleCode roleCode = form.getRole();
        if (roleCode == null || roleCode == RoleCode.ADMIN) {
            throw new GoogleOAuthException("Self-registration as ADMIN is not allowed");
        }
        Role role = roles.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Missing role: " + roleCode));

        // У Google-only аккаунта нет известного пользователю локального пароля. Случайный BCrypt hash
        // сохраняет NOT NULL-инвариант таблицы и не открывает обход OAuth через форму пароля.
        String unreachablePassword = passwordEncoder.encode(UUID.randomUUID().toString());
        AppUser user = new AppUser(identity.email(), unreachablePassword, form.getFullName().trim(), null);
        user.linkGoogleSubject(identity.subject());
        user.confirmEmail(OffsetDateTime.now());
        user.addRole(role);
        AppUser saved = users.save(user);

        if (roleCode == RoleCode.STUDENT) {
            studentProfiles.save(new StudentProfile(saved, form.getGradeNo(), form.getPreferredLanguage()));
        } else {
            parentProfiles.save(new ParentProfile(saved));
        }
        return saved;
    }
}
