package kz.damulab.quiz.catalog;

import kz.damulab.users.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Разрешает технический ID текущего администратора для обязательного редакционного аудита. */
@Service
public class CurrentAdminService {
    private final AppUserRepository users;
    public CurrentAdminService(AppUserRepository users) { this.users = users; }

    /** Возвращает ID аутентифицированного пользователя или отклоняет потерянную учётную запись. */
    public Long requireId(Authentication authentication) {
        if (authentication == null) throw new ContentValidationException("Требуется вход администратора");
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ContentValidationException("Учётная запись администратора не найдена"))
                .getId();
    }
}
