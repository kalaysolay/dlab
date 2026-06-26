package kz.damulab.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Включает поддержку @Async в Spring.
 *
 * Именованный executor "aiJobExecutor" используется для фоновых задач генерации вопросов через ИИ.
 * Пул небольшой (2–4 потока): AI job — долгий и блокирующий I/O вызов, одновременных запросов немного.
 * QueueCapacity=20 — буфер на случай всплеска; при переполнении Spring выбросит TaskRejectedException.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("aiJobExecutor")
    public Executor aiJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-job-");
        executor.initialize();
        return executor;
    }
}
