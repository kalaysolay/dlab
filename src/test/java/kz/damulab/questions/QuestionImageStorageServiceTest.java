package kz.damulab.questions;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionImageStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesPngInDedicatedDirectoryAndReturnsQuestionImageUrl() throws Exception {
        QuestionImageStorageService storage = new QuestionImageStorageService(tempDir.toString());
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};

        var stored = storage.store(new MockMultipartFile("file", "illustration.any", "text/plain", png));

        assertThat(stored.url()).startsWith("/files/question-images/").endsWith(".png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(Files.readAllBytes(tempDir.resolve(stored.storageKey()))).isEqualTo(png);
    }

    @Test
    void rejectsFileWhoseSignatureIsNotAnAllowedImage() {
        QuestionImageStorageService storage = new QuestionImageStorageService(tempDir.toString());
        MockMultipartFile fake = new MockMultipartFile(
                "file", "attack.svg", "image/svg+xml", "<svg><script/></svg>".getBytes()
        );

        assertThatThrownBy(() -> storage.store(fake))
                .isInstanceOf(QuestionImageException.class)
                .hasMessage("question_image_type_not_allowed");
    }
}
