package kz.damulab.parentlink;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
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

@Service
public class ParentLinkService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final ParentProfileRepository parentProfiles;
    private final StudentProfileRepository studentProfiles;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final ParentStudentLinkRepository links;
    private final LinkCodeRepository linkCodes;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final QrCodeSvgRenderer qrCodeSvgRenderer;
    private final SecureRandom random = new SecureRandom();

    public ParentLinkService(
            ParentProfileRepository parentProfiles,
            StudentProfileRepository studentProfiles,
            AppUserRepository users,
            RoleRepository roles,
            ParentStudentLinkRepository links,
            LinkCodeRepository linkCodes,
            PasswordEncoder passwordEncoder,
            Clock clock,
            QrCodeSvgRenderer qrCodeSvgRenderer
    ) {
        this.parentProfiles = parentProfiles;
        this.studentProfiles = studentProfiles;
        this.users = users;
        this.roles = roles;
        this.links = links;
        this.linkCodes = linkCodes;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.qrCodeSvgRenderer = qrCodeSvgRenderer;
    }

    @Transactional(readOnly = true)
    public List<ChildResponse> listChildren(String parentEmail) {
        ParentProfile parent = findParent(parentEmail);
        return links.findByParentProfileOrderByCreatedAtDesc(parent).stream()
                .map(link -> toChildResponse(link.getStudentProfile()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChildResponse getChild(String parentEmail, Long studentId) {
        ParentProfile parent = findParent(parentEmail);
        StudentProfile student = findStudent(studentId);
        requireLinked(parent, student);
        return toChildResponse(student);
    }

    @Transactional
    public ChildResponse createChild(String parentEmail, CreateChildForm form) {
        ParentProfile parent = findParent(parentEmail);
        String email = normalizeOrGenerateChildEmail(form.getEmail());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ParentLinkException("child_email_exists");
        }

        Role studentRole = roles.findByCode(RoleCode.STUDENT)
                .orElseThrow(() -> new IllegalStateException("Missing role: STUDENT"));
        AppUser user = new AppUser(
                email,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                form.getFullName().trim(),
                null
        );
        user.addRole(studentRole);
        AppUser savedUser = users.save(user);
        StudentProfile student = studentProfiles.save(new StudentProfile(
                savedUser,
                form.getGradeNo(),
                blankToDefaultLanguage(form.getPreferredLanguage())
        ));
        links.save(new ParentStudentLink(parent, student));
        return toChildResponse(student);
    }

    @Transactional
    public ChildResponse attachChildByCode(String parentEmail, String rawCode) {
        ParentProfile parent = findParent(parentEmail);
        String code = normalizeCode(rawCode);
        LinkCode linkCode = linkCodes.findByCode(code)
                .orElseThrow(() -> new ParentLinkException("link_code_not_found"));
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!linkCode.isUsable(now)) {
            throw new ParentLinkException("link_code_not_available");
        }

        StudentProfile student = linkCode.getStudentProfile();
        if (!links.existsByParentProfileAndStudentProfile(parent, student)) {
            links.save(new ParentStudentLink(parent, student));
        }
        linkCode.consume(now);
        return toChildResponse(student);
    }

    @Transactional
    public LinkCodeResponse createStudentLinkCode(String studentEmail) {
        StudentProfile student = studentProfiles.findByUserEmailIgnoreCase(studentEmail)
                .orElseThrow(() -> new ParentLinkException("student_not_found"));
        return createLinkCode(student);
    }

    @Transactional
    public LinkCodeResponse createOwnedChildLinkCode(String parentEmail, Long studentId) {
        ParentProfile parent = findParent(parentEmail);
        StudentProfile student = findStudent(studentId);
        requireLinked(parent, student);
        return createLinkCode(student);
    }

    @Transactional
    public void unlinkChild(String parentEmail, Long studentId) {
        ParentProfile parent = findParent(parentEmail);
        StudentProfile student = findStudent(studentId);
        requireLinked(parent, student);
        links.deleteByParentProfileAndStudentProfile(parent, student);
    }

    private LinkCodeResponse createLinkCode(StudentProfile student) {
        String code = generateUniqueCode();
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plusMinutes(15);
        LinkCode saved = linkCodes.save(new LinkCode(student, code, expiresAt));
        return new LinkCodeResponse(saved.getCode(), saved.getExpiresAt(), qrCodeSvgRenderer.render(saved.getCode()));
    }

    private ParentProfile findParent(String email) {
        return parentProfiles.findByUserEmailIgnoreCase(email)
                .orElseThrow(() -> new ParentLinkException("parent_not_found"));
    }

    private StudentProfile findStudent(Long studentId) {
        return studentProfiles.findById(studentId)
                .orElseThrow(() -> new ParentLinkException("student_not_found"));
    }

    private void requireLinked(ParentProfile parent, StudentProfile student) {
        links.findByParentProfileAndStudentProfile(parent, student)
                .orElseThrow(() -> new ParentLinkException("child_not_linked_to_parent"));
    }

    private ChildResponse toChildResponse(StudentProfile student) {
        AppUser user = student.getUser();
        return new ChildResponse(
                student.getId(),
                user.getEmail(),
                user.getFullName(),
                student.getGradeNo(),
                student.getPreferredLanguage()
        );
    }

    private String normalizeOrGenerateChildEmail(String email) {
        if (email == null || email.isBlank()) {
            return "child-" + UUID.randomUUID() + "@child.damulab.local";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToDefaultLanguage(String language) {
        return language == null || language.isBlank() ? "ru" : language;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = randomCode();
        } while (linkCodes.existsByCode(code));
        return code;
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
