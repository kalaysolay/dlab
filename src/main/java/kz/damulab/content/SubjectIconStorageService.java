package kz.damulab.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Хранит PNG-иконки предметов вне каталога со статическими ресурсами приложения. */
@Service
public class SubjectIconStorageService {

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final Pattern SAFE_KEY = Pattern.compile("[0-9a-f-]{36}\\.png");

    private final Path storageDirectory;

    /** Создаёт хранилище в настроенном каталоге. */
    public SubjectIconStorageService(
            @Value("${damulab.subjects.icons.storage-dir:uploads/subject-icons}") String storageDirectory
    ) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    /** Проверяет и сохраняет PNG, возвращая безопасный ключ файла. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SubjectManagementException("icon_empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new SubjectManagementException("icon_too_large");
        }

        try {
            byte[] content = file.getBytes();
            if (!hasPngSignature(content)) {
                throw new SubjectManagementException("icon_not_png");
            }
            Files.createDirectories(storageDirectory);
            String storageKey = UUID.randomUUID() + ".png";
            Files.write(resolve(storageKey), content, StandardOpenOption.CREATE_NEW);
            return storageKey;
        } catch (IOException ex) {
            throw new SubjectManagementException("icon_storage_failed");
        }
    }

    /** Загружает сохранённую иконку для HTTP-ответа. */
    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new SubjectManagementException("icon_not_found");
        }
        return new FileSystemResource(path);
    }

    /** Удаляет старую иконку; отсутствие файла не считается ошибкой. */
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ex) {
            throw new SubjectManagementException("icon_delete_failed");
        }
    }

    private boolean hasPngSignature(byte[] content) {
        if (content.length < PNG_SIGNATURE.length) {
            return false;
        }
        return Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(content, PNG_SIGNATURE.length));
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || !SAFE_KEY.matcher(storageKey).matches()) {
            throw new SubjectManagementException("icon_not_found");
        }
        Path path = storageDirectory.resolve(storageKey).normalize();
        if (!path.startsWith(storageDirectory)) {
            throw new SubjectManagementException("icon_not_found");
        }
        return path;
    }
}
