package kz.damulab.lectures;

import static org.assertj.core.api.Assertions.assertThat;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/** Проверяет сохранение ширины и фильтрацию небезопасных атрибутов изображения. */
class LectureImageWidthTest {
    private final LectureService service = new LectureService(null, null, null, null, null, null, null, null, null);

    @Test
    void preservesWidthButRemovesStylesAndHeight() {
        var html = service.sanitizeImportedRichText("<img src='/files/lecture-images/abc.png' width='45%' height='900' style='position:fixed' onload='alert(1)'>");
        var image = Jsoup.parseBodyFragment(html).selectFirst("img");
        assertThat(image.attr("width")).isEqualTo("45%");
        assertThat(image.hasAttr("height")).isFalse();
        assertThat(image.hasAttr("style")).isFalse();
        assertThat(image.hasAttr("onload")).isFalse();
        assertThat(service.sanitizeImportedRichText(html)).isEqualTo(html);
    }

    @Test
    void rejectsInvalidWidthsAndPreservesLegacyImages() {
        for (String width : new String[]{"", "0", "-10", "101%", "99999999", "50vw"}) {
            var html = service.sanitizeImportedRichText("<img src='/files/lecture-images/abc.png' width='" + width + "'>");
            var image = Jsoup.parseBodyFragment(html).selectFirst("img");
            assertThat(image).isNotNull();
            assertThat(image.hasAttr("width")).isFalse();
        }
    }
}
