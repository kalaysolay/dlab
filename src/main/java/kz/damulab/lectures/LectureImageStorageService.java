package kz.damulab.lectures;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Хранит изображения, вставленные непосредственно в HTML лекции.
 *
 * <p>Формат определяется по сигнатуре файла, а не по расширению или присланному
 * Content-Type. SVG намеренно запрещён, поскольку может содержать активный контент.</p>
 */
@Component
public class LectureImageStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Pattern SAFE_KEY = Pattern.compile("^[a-f0-9-]+\\.(png|jpg|gif|webp)$");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp"
    );

    private final Path storageRoot;

    public LectureImageStorageService(
            @Value("${damulab.lectures.images.storage-dir:uploads/lecture-images}") String storageDir
    ) {
        try {
            storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new LectureException("lecture_image_storage_unavailable");
        }
    }

    /** Проверяет изображение, сохраняет его под случайным ключом и возвращает публичный URL. */
    public StoredLectureImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new LectureException("lecture_image_required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new LectureException("lecture_image_too_large");
        }
        try (InputStream input = file.getInputStream()) {
            return store(input.readAllBytes());
        } catch (IOException ex) {
            throw new LectureException("lecture_image_read_failed");
        }
    }

    /**
     * Проверяет и сохраняет изображение, переданное внутри JSON-импорта как Base64.
     * Проверка общая с ручной загрузкой: Content-Type из JSON не считается доверенным,
     * тип определяется только по сигнатуре декодированных байтов.
     */
    public StoredLectureImage store(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new LectureException("lecture_image_required");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new LectureException("lecture_image_too_large");
        }
        String extension = detectExtension(bytes);
        if (extension == null) {
            throw new LectureException("lecture_image_type_not_allowed");
        }

        String key = UUID.randomUUID() + "." + extension;
        Path target = resolve(key);
        try {
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new LectureException("lecture_image_storage_failed");
        }
        return new StoredLectureImage(key, publicUrl(key), CONTENT_TYPES.get(extension), bytes.length);
    }

    /** Удаляет asset незавершённого импорта, чтобы ошибка JSON не оставляла сиротский файл. */
    public void deleteIfExists(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | LectureException ignored) {
            // Cleanup выполняется после исходной ошибки импорта и не должен скрывать её новой ошибкой.
        }
    }

    /** Возвращает ранее сохранённое изображение либо доменную ошибку для HTTP 404. */
    public Resource load(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new LectureException("lecture_image_not_found");
        }
        try {
            return new UrlResource(file.toUri());
        } catch (IOException ex) {
            throw new LectureException("lecture_image_not_found");
        }
    }

    /** Определяет безопасный Content-Type исключительно по проверенному ключу файла. */
    public String contentType(String key) {
        String extension = key.substring(key.lastIndexOf('.') + 1);
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    /** Формирует относительный URL, который разрешён sanitizer-ом лекций. */
    public String publicUrl(String key) {
        return "/files/lecture-images/" + key;
    }

    private Path resolve(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new LectureException("lecture_image_key_invalid");
        }
        Path resolved = storageRoot.resolve(key).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new LectureException("lecture_image_key_invalid");
        }
        return resolved;
    }

    private String detectExtension(byte[] bytes) {
        if (startsWith(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return "png";
        if (startsWith(bytes, 0xff, 0xd8, 0xff)) return "jpg";
        if (startsWith(bytes, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61)
                || startsWith(bytes, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61)) return "gif";
        if (bytes.length >= 12 && startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) return "webp";
        return null;
    }

    private boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xff) != signature[i]) return false;
        }
        return true;
    }

    public record StoredLectureImage(String storageKey, String url, String contentType, long sizeBytes) {
    }
}
