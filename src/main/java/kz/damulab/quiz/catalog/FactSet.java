package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Банк проверяемых фактов и правила источников; сами утверждения имеют версии. Связи без cascade REMOVE сохраняют историю. */
@Entity
@Table(name = "fact_sets")
public class FactSet extends QuizEditorialRecord {
    @Column(name = "code", length = 96, nullable = false)
    private String code;
    @Column(name = "title_ru", length = 255, nullable = false)
    private String titleRu;
    @Column(name = "title_kk", length = 255, nullable = false)
    private String titleKk;
    @Column(name = "source_policy", columnDefinition = "text", nullable = false)
    private String sourcePolicy;

    protected FactSet() {}

    /** Создаёт ещё не сохранённую запись; полноту публикации проверяет редакционный сервис. */
    public FactSet(String code, String titleRu, String titleKk, String sourcePolicy) {
        this.code = code;
        this.titleRu = titleRu;
        this.titleKk = titleKk;
        this.sourcePolicy = sourcePolicy;
    }

    /** Возвращает поле code. */
    public String getCode() { return code; }

    /** Возвращает поле title_ru. */
    public String getTitleRu() { return titleRu; }
    /** Задаёт title_ru для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setTitleRu(String value) { titleRu = value; }

    /** Возвращает поле title_kk. */
    public String getTitleKk() { return titleKk; }
    /** Задаёт title_kk для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setTitleKk(String value) { titleKk = value; }

    /** Возвращает поле source_policy. */
    public String getSourcePolicy() { return sourcePolicy; }
    /** Задаёт source_policy для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setSourcePolicy(String value) { sourcePolicy = value; }
}
