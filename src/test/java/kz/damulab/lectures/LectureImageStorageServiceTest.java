package kz.damulab.lectures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/** Проверяет сигнатуры и изоляцию файлового хранилища изображений лекций. */
class LectureImageStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesPngAndReturnsLectureImageUrl() throws Exception {
        LectureImageStorageService storage = new LectureImageStorageService(tempDir.toString());
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};

        var stored = storage.store(new MockMultipartFile("file", "screenshot.any", "text/plain", png));

        assertThat(stored.url()).startsWith("/files/lecture-images/").endsWith(".png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(Files.readAllBytes(tempDir.resolve(stored.storageKey()))).isEqualTo(png);
    }

    @Test
    void rejectsSvgEvenWhenDeclaredAsImage() {
        LectureImageStorageService storage = new LectureImageStorageService(tempDir.toString());
        MockMultipartFile svg = new MockMultipartFile(
                "file", "unsafe.svg", "image/svg+xml", "<svg><script/></svg>".getBytes()
        );

        assertThatThrownBy(() -> storage.store(svg))
                .isInstanceOf(LectureException.class)
                .hasMessage("lecture_image_type_not_allowed");
    }
}
