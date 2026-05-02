package kz.damulab.lectures;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LectureAttachmentStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L;
    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".webm", ".ogg", ".mov");

    private final Path storageRoot;

    public LectureAttachmentStorageService(
            @Value("${damulab.lectures.attachments.storage-dir:uploads/lecture-attachments}") String storageDir
    ) {
        try {
            this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(this.storageRoot);
        } catch (IOException ex) {
            throw new LectureException("lecture_attachment_storage_unavailable");
        }
    }

    public StoredAttachment store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new LectureException("lecture_attachment_file_required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new LectureException("lecture_attachment_file_too_large");
        }
        String originalName = safeOriginalFilename(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        String storageKey = UUID.randomUUID() + extension;
        Path target = resolveStoragePath(storageKey);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            String contentType = detectContentType(target, file.getContentType(), originalName);
            String mediaType = inferMediaType(originalName, contentType);
            return new StoredAttachment(
                    storageKey,
                    publicUrl(storageKey),
                    originalName,
                    contentType,
                    file.getSize(),
                    mediaType
            );
        } catch (IOException ex) {
            throw new LectureException("lecture_attachment_storage_failed");
        }
    }

    public Resource loadAsResource(String storageKey) {
        Path file = resolveStoragePath(storageKey);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new LectureException("lecture_attachment_file_not_found");
        }
        try {
            return new UrlResource(file.toUri());
        } catch (IOException ex) {
            throw new LectureException("lecture_attachment_file_not_found");
        }
    }

    public String detectContentType(String storageKey) {
        Path file = resolveStoragePath(storageKey);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            String detected = Files.probeContentType(file);
            if (detected == null || detected.isBlank()) {
                return MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            return detected;
        } catch (IOException ex) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    public void deleteIfExists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        Path file = resolveStoragePath(storageKey);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            // Cleanup is best-effort; stale files are acceptable and isolated in storageRoot.
        }
    }

    public String extractStorageKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        String prefix = "/files/lecture-attachments/";
        int prefixIndex = trimmed.indexOf(prefix);
        if (prefixIndex < 0) {
            return null;
        }
        String tail = trimmed.substring(prefixIndex + prefix.length());
        int queryIndex = tail.indexOf('?');
        if (queryIndex >= 0) {
            tail = tail.substring(0, queryIndex);
        }
        int fragmentIndex = tail.indexOf('#');
        if (fragmentIndex >= 0) {
            tail = tail.substring(0, fragmentIndex);
        }
        int slash = tail.indexOf('/');
        String key = slash >= 0 ? tail.substring(0, slash) : tail;
        return isSafeStorageKey(key) ? key : null;
    }

    public String publicUrl(String storageKey) {
        return "/files/lecture-attachments/" + storageKey;
    }

    private Path resolveStoragePath(String storageKey) {
        if (!isSafeStorageKey(storageKey)) {
            throw new LectureException("lecture_attachment_storage_key_invalid");
        }
        Path file = storageRoot.resolve(storageKey).normalize();
        if (!file.startsWith(storageRoot)) {
            throw new LectureException("lecture_attachment_storage_key_invalid");
        }
        return file;
    }

    private boolean isSafeStorageKey(String storageKey) {
        return storageKey != null
                && !storageKey.isBlank()
                && SAFE_KEY_PATTERN.matcher(storageKey).matches();
    }

    private String safeOriginalFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return "attachment.bin";
        }
        String unix = raw.replace('\\', '/');
        String name = unix.substring(unix.lastIndexOf('/') + 1).trim();
        if (name.isBlank()) {
            return "attachment.bin";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return ".bin";
        }
        String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
        if (ext.length() > 10) {
            return ".bin";
        }
        return ext;
    }

    private String detectContentType(Path file, String declaredType, String originalName) {
        String declared = declaredType == null ? "" : declaredType.trim().toLowerCase(Locale.ROOT);
        if (MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(declared) || declared.isBlank()) {
            try {
                String detected = Files.probeContentType(file);
                if (detected != null && !detected.isBlank()) {
                    return detected;
                }
            } catch (IOException ignored) {
                // fallback to extension.
            }
            String ext = extensionOf(originalName);
            if (".pdf".equals(ext)) {
                return MediaType.APPLICATION_PDF_VALUE;
            }
            if (IMAGE_EXTENSIONS.contains(ext)) {
                return "image/" + ext.substring(1).replace("jpg", "jpeg");
            }
            if (VIDEO_EXTENSIONS.contains(ext)) {
                return "video/" + ext.substring(1);
            }
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return declared;
    }

    private String inferMediaType(String originalName, String contentType) {
        String lowerType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String ext = extensionOf(originalName);
        if (lowerType.startsWith("image/") || IMAGE_EXTENSIONS.contains(ext)) {
            return "image";
        }
        if (lowerType.startsWith("video/") || VIDEO_EXTENSIONS.contains(ext)) {
            return "video";
        }
        if (MediaType.APPLICATION_PDF_VALUE.equals(lowerType) || ".pdf".equals(ext)) {
            return "pdf";
        }
        return "link";
    }

    public record StoredAttachment(
            String storageKey,
            String url,
            String originalFileName,
            String contentType,
            long sizeBytes,
            String mediaType
    ) {
    }
}

