package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Версия утверждения; false является полноценным ключом, не признаком отсутствия ответа. Связи без cascade REMOVE сохраняют историю. */
@Entity
@Table(name = "fact_statement_versions")
public class FactStatementVersion extends QuizVersionRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false)
    private FactStatement statement;
    @Column(name = "text_ru", columnDefinition = "text", nullable = false)
    private String textRu;
    @Column(name = "text_kk", columnDefinition = "text", nullable = false)
    private String textKk;
    @Column(name = "truth_value", nullable = false)
    private boolean truthValue;
    @Column(name = "explanation_ru", columnDefinition = "text", nullable = false)
    private String explanationRu;
    @Column(name = "explanation_kk", columnDefinition = "text", nullable = false)
    private String explanationKk;
    @Column(name = "source_reference", columnDefinition = "text", nullable = false)
    private String sourceReference;
    @Column(name = "universal_audience", nullable = false)
    private boolean universalAudience = false;
    @ElementCollection
    @CollectionTable(name = "fact_statement_grades", joinColumns = @JoinColumn(name = "statement_version_id"))
    @Column(name = "grade_id", nullable = false)
    private Set<Long> gradeIds = new LinkedHashSet<>();

    protected FactStatementVersion() {}

    /** Создаёт ещё не сохранённую запись; полноту публикации проверяет редакционный сервис. */
    public FactStatementVersion(int versionNo, FactStatement statement, String textRu, String textKk, boolean truthValue, String explanationRu, String explanationKk, String sourceReference) {
        super(versionNo);
        this.statement = statement;
        this.textRu = textRu;
        this.textKk = textKk;
        this.truthValue = truthValue;
        this.explanationRu = explanationRu;
        this.explanationKk = explanationKk;
        this.sourceReference = sourceReference;
    }

    /** Возвращает поле statement_id. */
    public FactStatement getStatement() { return statement; }

    /** Возвращает поле text_ru. */
    public String getTextRu() { return textRu; }
    /** Задаёт text_ru для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setTextRu(String value) { textRu = value; }

    /** Возвращает поле text_kk. */
    public String getTextKk() { return textKk; }
    /** Задаёт text_kk для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setTextKk(String value) { textKk = value; }

    /** Возвращает поле truth_value. */
    public boolean isTruthValue() { return truthValue; }
    /** Задаёт truth_value для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setTruthValue(boolean value) { truthValue = value; }

    /** Возвращает поле explanation_ru. */
    public String getExplanationRu() { return explanationRu; }
    /** Задаёт explanation_ru для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setExplanationRu(String value) { explanationRu = value; }

    /** Возвращает поле explanation_kk. */
    public String getExplanationKk() { return explanationKk; }
    /** Задаёт explanation_kk для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setExplanationKk(String value) { explanationKk = value; }

    /** Возвращает поле source_reference. */
    public String getSourceReference() { return sourceReference; }
    /** Задаёт source_reference для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setSourceReference(String value) { sourceReference = value; }

    /** Возвращает поле universal_audience. */
    public boolean isUniversalAudience() { return universalAudience; }
    /** Задаёт universal_audience для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setUniversalAudience(boolean value) { universalAudience = value; }

    /** Возвращает классы версии; пустой набор не означает universal. */
    public Set<Long> getGradeIds() { return Collections.unmodifiableSet(gradeIds); }
    /** Задаёт классы версии; существование grade_id проверяет FK. */
    public void setGradeIds(Set<Long> value) { gradeIds = new LinkedHashSet<>(value); }
}
