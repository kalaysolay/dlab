package kz.damulab.parentreports;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Медленная внешняя доставка не должна задерживать таймеры учебных викторин. */
@Configuration
public class TelegramSchedulingConfig {
    @Bean
    ThreadPoolTaskScheduler parentTelegramPollingScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("telegram-polling-");
        return scheduler;
    }
    /** Сохраняем отдельный стандартный планировщик остальных задач приложения. */
    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduling-");
        return scheduler;
    }
    @Bean
    ThreadPoolTaskScheduler parentReportTaskScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("parent-reports-");
        return scheduler;
    }
}
