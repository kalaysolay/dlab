package kz.damulab.questions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Хранит иллюстрации, вставленные непосредственно в HTML вопроса.
 *
 * <p>Каталог намеренно отделён от вложений лекций. Тип определяется по сигнатуре файла,
 * а не по имени или присланному Content-Type; SVG запрещён, поскольку может содержать скрипты.</p>
 */
@Component
public class QuestionImageStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Pattern SAFE_KEY = Pattern.compile("^[a-f0-9-]+\\.(png|jpg|gif|webp)$");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp"
    );

    private final Path storageRoot;

    public QuestionImageStorageService(
            @Value("${damulab.questions.images.storage-dir:uploads/question-images}") String storageDir
    ) {
        try {
            storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new QuestionImageException("question_image_storage_unavailable");
        }
    }

    /** Проверяет изображение, записывает под случайным ключом и возвращает URL для HTML редактора. */
    public StoredQuestionImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new QuestionImageException("question_image_required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new QuestionImageException("question_image_too_large");
        }
        String extension;
        try (InputStream input = file.getInputStream()) {
            extension = detectExtension(input.readNBytes(16));
        } catch (IOException ex) {
            throw new QuestionImageException("question_image_read_failed");
        }
        if (extension == null) {
            throw new QuestionImageException("question_image_type_not_allowed");
        }

        String key = UUID.randomUUID() + "." + extension;
        Path target = resolve(key);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new QuestionImageException("question_image_storage_failed");
        }
        return new StoredQuestionImage(key, publicUrl(key), CONTENT_TYPES.get(extension), file.getSize());
    }

    public Resource load(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new QuestionImageException("question_image_not_found");
        }
        try {
            return new UrlResource(file.toUri());
        } catch (IOException ex) {
            throw new QuestionImageException("question_image_not_found");
        }
    }

    public String contentType(String key) {
        String extension = key.substring(key.lastIndexOf('.') + 1);
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    public String publicUrl(String key) {
        return "/files/question-images/" + key;
    }

    private Path resolve(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new QuestionImageException("question_image_key_invalid");
        }
        Path resolved = storageRoot.resolve(key).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new QuestionImageException("question_image_key_invalid");
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

    public record StoredQuestionImage(String storageKey, String url, String contentType, long sizeBytes) {
    }
}
