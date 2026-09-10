package kz.damulab.lectures;

import static org.assertj.core.api.Assertions.assertThat;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/** Проверяет сохранение плеера между абзацами и запрет произвольного iframe-контента. */
class LectureYouTubeTest {
    private final LectureService service = new LectureService(null, null, null, null, null, null, null, null, null);

    @Test
    void preservesVideoPositionAndSurvivesReopening() {
        String html = service.sanitizeImportedRichText("<p>Before</p><iframe src='https://www.youtube.com/embed/M7lc1UVf-VE' srcdoc='bad' onload='bad' style='position:fixed'></iframe><p>After</p>");
        var body = Jsoup.parseBodyFragment(html).body();
        assertThat(body.children().stream().map(org.jsoup.nodes.Element::tagName).toList()).containsExactly("p", "iframe", "p");
        var frame = body.child(1);
        assertThat(frame.attr("src")).isEqualTo("https://www.youtube.com/embed/M7lc1UVf-VE");
        assertThat(frame.className()).isEqualTo("ql-video");
        assertThat(frame.hasAttr("srcdoc")).isFalse();
        assertThat(frame.hasAttr("onload")).isFalse();
        assertThat(frame.hasAttr("style")).isFalse();
        assertThat(frame.hasAttr("allowfullscreen")).isTrue();
        assertThat(service.sanitizeImportedRichText(html)).isEqualTo(html);
    }

    @Test
    void removesUntrustedFrames() {
        for (String src : new String[]{"https://evil.org/embed/M7lc1UVf-VE", "https://www.youtube.com.evil.org/embed/M7lc1UVf-VE", "javascript:alert(1)", "//www.youtube.com/embed/M7lc1UVf-VE", "https://www.youtube.com/embed/M7lc1UVf-VE?autoplay=1", "https://www.youtube.com/embed/bad"}) {
            String html = service.sanitizeImportedRichText("<p>Keep</p><iframe src='" + src + "'></iframe>");
            assertThat(Jsoup.parseBodyFragment(html).select("iframe")).isEmpty();
            assertThat(html).contains("Keep");
        }
    }
}
