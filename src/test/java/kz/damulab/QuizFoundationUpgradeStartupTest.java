package kz.damulab;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import kz.damulab.config.DamulabQuizProperties;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.QuizContentKind;

/**
 * Запускает именно приложение поверх V46 с сохранённой историей.
 * Spring Flyway автоматически обновляет выделенную схему, Hibernate validate проверяет
 * весь mapping, а JPA читает прежние комнаты/ответы/результаты без новых обязательных полей.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuizFoundationUpgradeStartupTest {
    @Autowired EntityManager em;
    @Autowired QuizRoomRepository rooms;
    @Autowired DamulabQuizProperties properties;

    @DynamicPropertySource
    static void upgradedSchema(DynamicPropertyRegistry registry) throws Exception {
        String schema = "quiz_startup_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv().getOrDefault("QUIZ_TEST_DB_URL", "jdbc:h2:mem:" + schema + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        String user = System.getenv().getOrDefault("QUIZ_TEST_DB_USER", "sa");
        String password = System.getenv().getOrDefault("QUIZ_TEST_DB_PASSWORD", "");
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("46").load().migrate();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            connection.setSchema(schema); QuizFoundationMigrationTest.seedLegacy(connection);
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> url.startsWith("jdbc:postgresql:") ? "org.postgresql.Driver" : "org.h2.Driver");
        registry.add("spring.datasource.hikari.schema", () -> schema);
        registry.add("spring.flyway.schemas", () -> schema);
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
    }

    @Test
    void upgradedApplicationReadsLegacyHistoryAndKeepsCatalogOff() {
        assertThat(properties.isGameCatalogEnabled()).isFalse();
        assertThat(rooms.findByCodeIgnoreCase("legacy0").orElseThrow().getStatus()).isEqualTo(QuizRoomStatus.WAITING);
        assertThat(rooms.findByCodeIgnoreCase("legacy1").orElseThrow().getStatus()).isEqualTo(QuizRoomStatus.ACTIVE);
        var finished = rooms.findByCodeIgnoreCase("legacy2").orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(QuizRoomStatus.FINISHED);
        assertThat(finished.getGameVersion()).isNull();
        assertThat(finished.getSettingsRevision()).isZero();
        assertThat(finished.getFinishedAt()).isNotNull();
        var round = em.find(QuizRound.class, 900003L);
        assertThat(round.getContentKind()).isEqualTo(QuizContentKind.QUESTION);
        assertThat(round.getQuestionVersion().getId()).isEqualTo(900001L);
        assertThat(round.getPromptSnapshot()).isNull();
        var answer = em.find(QuizAnswer.class, 900003L);
        assertThat(answer.isCorrect()).isTrue();
        assertThat(answer.getPointsAwarded()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(answer.getResponseMs()).isNull();
        assertThat(answer.isTimedOut()).isFalse();
        assertThat(answer.isSkipped()).isFalse();
        var result = em.find(QuizResult.class, 900003L);
        assertThat(result.getPercent()).isEqualTo(100);
        assertThat(result.getScore()).isEqualByComparingTo(BigDecimal.ONE);
    }
}
