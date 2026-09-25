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

    @Test
    void preservesQuillLessonMediaAndCanonicalMarkup() {
        String html = service.sanitizeImportedRichText("""
                <h2>Доли и проценты</h2>
                <p>Формула <span class='ql-formula extra' data-value='\\frac{25}{100}=0.25'><span>старый KaTeX DOM</span></span></p>
                <img src='/files/lecture-images/11111111-1111-1111-1111-111111111111.webp'
                     width='80%' alt='Круговая диаграмма с четвертью круга' title='25 процентов'>
                <img src='/files/lecture-images/22222222-2222-2222-2222-222222222222.gif'
                     alt='Анимация деления круга на четыре части'>
                """);

        var body = Jsoup.parseBodyFragment(html).body();
        assertThat(body.selectFirst("h2").text()).isEqualTo("Доли и проценты");
        var formula = body.selectFirst(".ql-formula");
        assertThat(formula.attr("data-value")).isEqualTo("\\frac{25}{100}=0.25");
        assertThat(formula.attr("contenteditable")).isEqualTo("false");
        assertThat(formula.children()).isEmpty();
        assertThat(body.select("img")).hasSize(2);
        assertThat(body.select("img").get(0).attr("width")).isEqualTo("80%");
        assertThat(body.select("img").get(0).attr("alt")).isEqualTo("Круговая диаграмма с четвертью круга");
        assertThat(body.select("img").get(1).attr("src")).endsWith(".gif");
        assertThat(service.sanitizeImportedRichText(html)).isEqualTo(html);
    }

    @Test
    void removesExternalAndExecutableMediaWithoutLosingText() {
        String html = service.sanitizeImportedRichText("""
                <h3 onclick='alert(1)'>Безопасный текст</h3>
                <img src='data:image/png;base64,AAAA' onerror='alert(1)'>
                <img src='https://tracker.example/pixel.gif'>
                <iframe src='https://example.org/embed/demo' srcdoc='<script>alert(1)</script>'></iframe>
                <span class='ql-formula' data-value='\\href{javascript:alert(1)}{x}'>опасная формула</span>
                """);

        var body = Jsoup.parseBodyFragment(html).body();
        assertThat(body.selectFirst("h3").text()).isEqualTo("Безопасный текст");
        assertThat(body.selectFirst("h3").hasAttr("onclick")).isFalse();
        assertThat(body.select("img, iframe, .ql-formula")).isEmpty();
        assertThat(html).doesNotContain("javascript:", "data:image", "tracker.example", "srcdoc");
    }
}
