package kz.damulab.parentreports;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Один факт успешного открытия ученического кабинета в день, без истории URL и содержимого. */
@Configuration
public class StudentVisitTrackingConfig implements WebMvcConfigurer {
    private static final Logger log = LoggerFactory.getLogger(StudentVisitTrackingConfig.class);
    private final JdbcTemplate db;
    private final Clock clock;
    private final ZoneId zone;
    private final ConcurrentHashMap<String, LocalDate> seen = new ConcurrentHashMap<>();

    public StudentVisitTrackingConfig(JdbcTemplate db, Clock clock,
            @Value("${damulab.ui.server-time-zone:Asia/Almaty}") String zone) {
        this.db = db;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                    Object handler, Exception exception) {
                // Активы, фоновые запросы и неуспешные ответы не считаем посещением кабинета.
                if (!"GET".equals(request.getMethod()) || exception != null || response.getStatus() >= 400
                        || response.getContentType() == null || !response.getContentType().startsWith("text/html")
                        || request.getUserPrincipal() == null || !request.isUserInRole("STUDENT")) return;
                record(request.getUserPrincipal().getName());
            }
        }).addPathPatterns("/student", "/student/**");
    }

    /** Ошибка необязательного учёта не должна мешать ученику открыть урок. */
    private void record(String email) {
        LocalDate day = clock.instant().atZone(zone).toLocalDate();
        if (day.equals(seen.get(email))) return;
        try {
            // Уникальный ключ и ON CONFLICT защищают несколько вкладок и экземпляров приложения.
            db.update("insert into student_visit_days(student_id,visit_day) "
                    + "select s.id,? from student_profiles s join app_users u on u.id=s.user_id "
                    + "where lower(u.email)=lower(?) and u.enabled=true on conflict do nothing", day, email);
            if (seen.size() >= 10000) seen.clear();
            seen.put(email, day);
        } catch (org.springframework.dao.DataAccessException ex) {
            log.warn("Не удалось записать посещение ученика ({})", ex.getClass().getSimpleName());
        }
    }
}
