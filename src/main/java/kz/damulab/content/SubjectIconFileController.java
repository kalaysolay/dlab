package kz.damulab.content;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Отдаёт сохранённые PNG-иконки авторизованным пользователям. */
@Controller
public class SubjectIconFileController {

    private final SubjectIconStorageService storage;

    /** Подключает файловое хранилище предметных иконок. */
    public SubjectIconFileController(SubjectIconStorageService storage) {
        this.storage = storage;
    }

    /** Возвращает PNG с кешированием или 404 для неизвестного ключа. */
    @GetMapping("/files/subject-icons/{storageKey}")
    ResponseEntity<Resource> icon(@PathVariable String storageKey) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                    .body(storage.load(storageKey));
        } catch (SubjectManagementException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
