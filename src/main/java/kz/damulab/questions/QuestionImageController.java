package kz.damulab.questions;

import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/** HTTP-точки загрузки администратором и показа иллюстраций ученикам. */
@Controller
public class QuestionImageController {

    private final QuestionImageStorageService storage;

    public QuestionImageController(QuestionImageStorageService storage) {
        this.storage = storage;
    }

    /** Загружает одно изображение из Quill; доступ ограничен ADMIN в {@code SecurityConfig}. */
    @PostMapping(path = "/api/admin/question-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(storage.store(file));
        } catch (QuestionImageException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getCode()));
        }
    }

    /** Отдаёт только файлы из отдельного каталога вопросных иллюстраций. */
    @GetMapping("/files/question-images/{storageKey}")
    ResponseEntity<Resource> image(@PathVariable String storageKey) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(storage.contentType(storageKey)))
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(storage.load(storageKey));
        } catch (QuestionImageException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
