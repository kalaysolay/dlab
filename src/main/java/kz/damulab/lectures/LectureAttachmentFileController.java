package kz.damulab.lectures;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class LectureAttachmentFileController {

    private final LectureAttachmentStorageService storage;

    public LectureAttachmentFileController(LectureAttachmentStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/files/lecture-attachments/{storageKey}")
    ResponseEntity<Resource> attachment(@PathVariable String storageKey) {
        try {
            Resource resource = storage.loadAsResource(storageKey);
            String contentType = storage.detectContentType(storageKey);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                    .body(resource);
        } catch (LectureException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
