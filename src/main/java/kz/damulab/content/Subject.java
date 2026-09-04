package kz.damulab.content;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Учебный предмет. Код остаётся стабильным техническим идентификатором, а
 * названия, описания и иконку администратор может менять.
 */
@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Column(name = "title_kk", nullable = false)
    private String titleKk;

    @Column(name = "description_ru", nullable = false, length = 2000)
    private String descriptionRu;

    @Column(name = "description_kk", nullable = false, length = 2000)
    private String descriptionKk;

    @Column(name = "icon_storage_key", length = 64)
    private String iconStorageKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Subject() {
    }

    /** Создаёт предмет с автоматически сформированным стабильным кодом. */
    public Subject(String code, String titleRu, String titleKk, String descriptionRu, String descriptionKk) {
        this.code = code;
        update(titleRu, titleKk, descriptionRu, descriptionKk);
    }

    /** Обновляет редактируемые администратором текстовые данные предмета. */
    public void update(String titleRu, String titleKk, String descriptionRu, String descriptionKk) {
        this.titleRu = titleRu;
        this.titleKk = titleKk;
        this.descriptionRu = descriptionRu;
        this.descriptionKk = descriptionKk;
    }

    /** Сохраняет ключ PNG-файла или очищает его при удалении иконки. */
    public void setIconStorageKey(String iconStorageKey) {
        this.iconStorageKey = iconStorageKey;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitleRu() {
        return titleRu;
    }

    public String getTitleKk() {
        return titleKk;
    }

    /** Возвращает русское описание. */
    public String getDescriptionRu() {
        return descriptionRu;
    }

    /** Возвращает казахское описание. */
    public String getDescriptionKk() {
        return descriptionKk;
    }

    /** Возвращает ключ сохранённой иконки или {@code null}. */
    public String getIconStorageKey() {
        return iconStorageKey;
    }
}
