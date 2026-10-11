package kz.damulab.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/** Проверяет сроки ожидания, редакционное правило и удаление переключателя старого портала. */
class DamulabQuizPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void waitingTimeoutHasSafeDefaultBindsDurationAndRejectsNonPositiveValues() {
        contextRunner.run(context -> assertThat(context.getBean(DamulabQuizProperties.class).getWaitingTimeout()).isEqualTo(java.time.Duration.ofMinutes(30)));
        contextRunner.withPropertyValues("damulab.quiz.waiting-timeout=45m")
                .run(context -> assertThat(context.getBean(DamulabQuizProperties.class).getWaitingTimeout()).isEqualTo(java.time.Duration.ofMinutes(45)));
        for (String value : java.util.List.of("0s", "-1m", "invalid"))
            contextRunner.withPropertyValues("damulab.quiz.waiting-timeout=" + value).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void strictSelfReviewIsDisabledByDefaultAndCanBeEnabled() {
        contextRunner.run(context -> assertThat(context.getBean(DamulabQuizProperties.class).isStrictSelfReview()).isFalse());
        contextRunner.withPropertyValues("damulab.quiz.strict-self-review=true")
                .run(context -> assertThat(context.getBean(DamulabQuizProperties.class).isStrictSelfReview()).isTrue());
    }

    @Test
    void obsoleteCatalogFlagIsIgnoredInsteadOfSwitchingPortal() {
        contextRunner.withPropertyValues("damulab.quiz.game-catalog-enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void applicationYamlHasNoLegacyPortalSwitch() throws IOException {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));

        assertThat(sources.get(0).getProperty("damulab.quiz.game-catalog-enabled"))
                .isNull();
    }

    @Test
    void testProfileHasNoLegacyPortalSwitch() throws IOException {
        var sources = new YamlPropertySourceLoader().load("application-test", new ClassPathResource("application-test.yml"));

        assertThat(sources.get(0).getProperty("damulab.quiz.game-catalog-enabled")).isNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DamulabQuizProperties.class)
    static class PropertiesConfiguration {
    }
}
