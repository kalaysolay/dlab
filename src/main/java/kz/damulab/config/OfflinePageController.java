package kz.damulab.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Отдаёт офлайн-страницу Thymeleaf по маршруту GET /offline.
 * Service worker кэширует этот путь в SHELL_ASSETS и возвращает его
 * как fallback при отсутствии сети и закэшированного ответа.
 * Маршрут открыт публично (SecurityConfig.permitAll), аутентификация не требуется.
 */
@Controller
public class OfflinePageController {

    @GetMapping("/offline")
    String offlinePage() {
        return "offline";
    }
}
