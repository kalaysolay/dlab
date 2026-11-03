package kz.damulab.speaking;

import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Проверяет роль, флаг и пилотный допуск на сервере, включая прямые обращения к API. */
@Service
public class SpeakingAccess {
    private final SpeakingProperties properties;
    private final StudentProfileRepository students;
    public SpeakingAccess(SpeakingProperties properties, StudentProfileRepository students) {
        this.properties = properties;
        this.students = students;
    }
    /** Скрывает вход в разговорник, пока модуль или доступ ученика выключены. */
    @Transactional(readOnly = true)
    public boolean available() {
        try { student(false); return true; }
        catch (ResponseStatusException ex) { return false; }
    }
    /** При записи блокируем профиль: разные вкладки одного ученика не создадут две активные сессии. */
    StudentProfile student(boolean lock) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!properties.isEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaking_disabled");
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_STUDENT"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "speaking_student_required");
        }
        var profile = (lock ? students.findLockedByEmail(auth.getName()) : students.findByUserEmailIgnoreCase(auth.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "speaking_student_not_found"));
        if (properties.isPilotOnly() && !properties.getAllowedStudentIds().contains(profile.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "speaking_pilot_required");
        }
        return profile;
    }
}
