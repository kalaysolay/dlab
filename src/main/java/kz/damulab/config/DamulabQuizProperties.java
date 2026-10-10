package kz.damulab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Типизированные настройки викторин из {@code damulab.quiz.*}.
 *
 * <p>Флаг {@code damulab.quiz.game-catalog-enabled} по умолчанию выключен: поставку
 * схемы и административных банков можно подготовить без переключения ученического
 * портала. Ученический SSR-фасад читает флаг для выбора входа; запуск и расширенная
 * совместимость принимаются отдельно в COMP-03. Флаг не должен
 * отключать обслуживание ранее созданных комнат, результатов и timeout scheduler.
 */
@Component
@ConfigurationProperties(prefix = "damulab.quiz")
public class DamulabQuizProperties {

    private boolean gameCatalogEnabled = false;
    private boolean strictSelfReview = false;
    private java.time.Duration waitingTimeout = java.time.Duration.ofMinutes(30);

    /** Срок WAITING из damulab.quiz.waiting-timeout; не влияет на расписание ACTIVE. */
    public java.time.Duration getWaitingTimeout() { return waitingTimeout; }
    /** Задаёт положительный срок ожидания, по умолчанию 30 минут. */
    public void setWaitingTimeout(java.time.Duration value) {
        if (value == null || value.isNegative() || value.isZero()) throw new IllegalArgumentException("Срок ожидания должен быть положительным");
        waitingTimeout = value;
    }

    /** Возвращает явно настроенное включение каталога; без настройки результат false. */
    public boolean isGameCatalogEnabled() {
        return gameCatalogEnabled;
    }

    /** Применяет значение конфигурации Spring; не переключает комнаты или маршруты само по себе. */
    public void setGameCatalogEnabled(boolean gameCatalogEnabled) {
        this.gameCatalogEnabled = gameCatalogEnabled;
    }

    /** Возвращает запрет автору одобрять собственную редакцию; по умолчанию запрет выключен. */
    public boolean isStrictSelfReview() { return strictSelfReview; }

    /** Настраивает редакционное правило {@code damulab.quiz.strict-self-review}. */
    public void setStrictSelfReview(boolean strictSelfReview) { this.strictSelfReview = strictSelfReview; }
}
