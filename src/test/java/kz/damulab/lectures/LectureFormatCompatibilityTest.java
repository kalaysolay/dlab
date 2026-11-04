package kz.damulab.lectures;

import static org.assertj.core.api.Assertions.assertThat;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/** Проверяет потери формата, воспроизведённые на двух уроках после сохранения в Quill. */
class LectureFormatCompatibilityTest {
    private final LectureService service = new LectureService(null, null, null, null, null, null, null, null, null);

    @Test
    void restoresTableHeadersWithoutTrustingArbitraryAttributes() {
        String html = service.sanitizeImportedRichText("""
                <table><tbody><tr><td data-lecture-header='true'>Участник</td>
                <td data-lecture-header='true'>Обязанности</td></tr>
                <tr><td data-lecture-header='invalid'>Қаған</td><td>Басқару</td></tr></tbody></table>
                """);
        var doc = Jsoup.parseBodyFragment(html);
        assertThat(doc.select("tr").get(0).children()).hasSize(2);
        assertThat(doc.select("th[scope=col]")).hasSize(2);
        assertThat(doc.select("td")).hasSize(2);
        assertThat(html).doesNotContain("data-lecture-header");
        assertThat(service.sanitizeImportedRichText(html)).isEqualTo(html);
    }

    @Test
    void restoresMixedAndNestedListsToNativeHtml() {
        String html = service.sanitizeImportedRichText("""
                <ol><li data-list='bullet'><span class='ql-ui' contenteditable='false'></span>Источник</li>
                <li data-list='ordered' class='ql-indent-1'>Вложенный шаг</li>
                <li data-list='ordered'>Первый шаг</li><li data-list='ordered'>Второй шаг</li>
                <li data-list='bullet'>Ещё источник</li></ol>
                """);
        var doc = Jsoup.parseBodyFragment(html);
        assertThat(doc.body().children().stream().map(e -> e.tagName()).toList()).containsExactly("ul", "ol", "ul");
        assertThat(doc.select("ul > li > ol > li").text()).isEqualTo("Вложенный шаг");
        assertThat(doc.select("li")).hasSize(5);
        assertThat(html).doesNotContain("data-list", "ql-ui", "ql-indent");
        assertThat(service.sanitizeImportedRichText(html)).isEqualTo(html);
    }

    @Test
    void preservesOnlySupportedFormulaDisplayModesAndLatex() {
        String html = service.sanitizeImportedRichText("""
                <p><span class='ql-formula' data-value='\\frac{1}{2}' data-display='block'>старый DOM</span></p>
                <p><span class='ql-formula' data-value='x' data-display='inline'></span></p>
                <p><span class='ql-formula' data-value='y' data-display='javascript:alert(1)' onclick='alert(1)'></span></p>
                """);
        var spans = Jsoup.parseBodyFragment(html).select(".ql-formula");
        assertThat(spans.get(0).attr("data-value")).isEqualTo("\\frac{1}{2}");
        assertThat(spans.get(0).attr("data-display")).isEqualTo("block");
        assertThat(spans.get(1).attr("data-display")).isEqualTo("inline");
        assertThat(spans.get(2).hasAttr("data-display")).isFalse();
        assertThat(spans.get(0).children()).isEmpty();
        assertThat(html).doesNotContain("onclick", "javascript:", "старый DOM");
        assertThat(service.sanitizeImportedRichText(html)).isEqualTo(html);
    }

    @Test
    void preservesEditableQuestionPairsCalloutsAndGoals() {
        String html = service.sanitizeImportedRichText("""
                <h2 data-lecture-section='goals'>Цели</h2><ul><li>Сравнивать дроби</li></ul>
                <blockquote data-lecture-tone='warning'><p>Не дели на нуль</p></blockquote>
                <p data-lecture-question='check-1' data-lecture-part='prompt'>Сколько будет один плюс один?</p>
                <p data-lecture-question='check-1' data-lecture-part='answer'>Два</p>
                """);
        var doc = Jsoup.parseBodyFragment(html);
        assertThat(doc.select("[data-lecture-question=check-1]")).hasSize(2);
        assertThat(doc.selectFirst("[data-lecture-part=answer]").text()).isEqualTo("Два");
        assertThat(doc.selectFirst("blockquote").attr("data-lecture-tone")).isEqualTo("warning");
        assertThat(doc.selectFirst("h2").attr("data-lecture-section")).isEqualTo("goals");
        assertThat(service.sanitizeImportedRichText(html)).isEqualTo(html);
    }

    @Test
    void invalidSemanticMarkersNeverHideOrRemoveText() {
        String html = service.sanitizeImportedRichText("""
                <p data-lecture-question='bad id' data-lecture-part='answer' onclick='bad()'>Видимый текст</p>
                <blockquote data-lecture-tone='javascript:bad()'>Пояснение</blockquote>
                <h2 data-lecture-section='unknown'>Раздел</h2>
                """);
        assertThat(html).contains("Видимый текст", "Пояснение", "Раздел");
        assertThat(html).doesNotContain("data-lecture-", "onclick", "javascript:");
    }

    @Test
    void preservesMergedCellsAndRowHeadersAfterQuill() {
        String html = service.sanitizeImportedRichText("""
                <table><tbody><tr><td data-lecture-header='true' data-lecture-scope='col' rowspan='2'>Группа</td>
                <td data-lecture-header='true' data-lecture-scope='colgroup' colspan='2'>Периоды</td></tr>
                <tr><td data-lecture-header='true'>Начало</td><td data-lecture-header='true'>Конец</td></tr>
                <tr><td data-lecture-header='true' data-lecture-scope='row'>A</td><td colspan='2'>1–2</td></tr>
                </tbody></table>
                """);
        var doc = Jsoup.parseBodyFragment(html);
        assertThat(doc.selectFirst("th[rowspan=2][scope=col]").text()).isEqualTo("Группа");
        assertThat(doc.selectFirst("th[colspan=2][scope=colgroup]").text()).isEqualTo("Периоды");
        assertThat(doc.selectFirst("th[scope=row]").text()).isEqualTo("A");
        assertThat(doc.selectFirst("td[colspan=2]").text()).isEqualTo("1–2");
        assertThat(html).doesNotContain("data-lecture-header", "data-lecture-scope");
        assertThat(service.sanitizeImportedRichText(html)).isEqualTo(html);
    }

    @Test
    void removesInvalidCellGeometryAndScopeWithoutRemovingContents() {
        String html = service.sanitizeImportedRichText("""
                <table><tr><th rowspan='0' colspan='100000' scope='bad'>Заголовок</th>
                <td data-lecture-scope='row' rowspan='-1' colspan='2px'>Данные</td>
                <td rowspan='32' colspan='32'>Граница</td></tr></table>
                """);
        var doc = Jsoup.parseBodyFragment(html);
        assertThat(doc.selectFirst("th").attributes().size()).isZero();
        assertThat(doc.selectFirst("td").attributes().size()).isZero();
        assertThat(doc.select("[rowspan=32][colspan=32]")).hasSize(1);
        assertThat(doc.text()).contains("Заголовок", "Данные", "Граница");
    }
}
