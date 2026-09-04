package kz.damulab.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Поля административной формы создания и редактирования предмета. */
public class SubjectForm {

    @NotBlank
    @Size(max = 255)
    private String titleRu;

    @NotBlank
    @Size(max = 255)
    private String titleKk;

    @NotBlank
    @Size(max = 2000)
    private String descriptionRu;

    @NotBlank
    @Size(max = 2000)
    private String descriptionKk;

    private boolean removeIcon;

    /** Возвращает русское название. */
    public String getTitleRu() {
        return titleRu;
    }

    /** Устанавливает русское название. */
    public void setTitleRu(String titleRu) {
        this.titleRu = titleRu;
    }

    /** Возвращает казахское название. */
    public String getTitleKk() {
        return titleKk;
    }

    /** Устанавливает казахское название. */
    public void setTitleKk(String titleKk) {
        this.titleKk = titleKk;
    }

    /** Возвращает русское описание. */
    public String getDescriptionRu() {
        return descriptionRu;
    }

    /** Устанавливает русское описание. */
    public void setDescriptionRu(String descriptionRu) {
        this.descriptionRu = descriptionRu;
    }

    /** Возвращает казахское описание. */
    public String getDescriptionKk() {
        return descriptionKk;
    }

    /** Устанавливает казахское описание. */
    public void setDescriptionKk(String descriptionKk) {
        this.descriptionKk = descriptionKk;
    }

    /** Показывает, запросил ли администратор удаление текущей иконки. */
    public boolean isRemoveIcon() {
        return removeIcon;
    }

    /** Устанавливает признак удаления текущей иконки. */
    public void setRemoveIcon(boolean removeIcon) {
        this.removeIcon = removeIcon;
    }
}
