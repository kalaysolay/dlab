package kz.damulab.lectures;

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

/** HTTP-точки загрузки администратором и показа встроенных изображений лекций. */
@Controller
public class LectureImageController {

    private final LectureImageStorageService storage;

    public LectureImageController(LectureImageStorageService storage) {
        this.storage = storage;
    }

    /** Загружает одно изображение из Quill; {@code /api/admin/**} доступен только ADMIN. */
    @PostMapping(path = "/api/admin/lecture-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(storage.store(file));
        } catch (LectureException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getCode()));
        }
    }

    /** Отдаёт только файл с ключом, прошедшим строгую проверку хранилища. */
    @GetMapping("/files/lecture-images/{storageKey}")
    ResponseEntity<Resource> image(@PathVariable String storageKey) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(storage.contentType(storageKey)))
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(storage.load(storageKey));
        } catch (LectureException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
