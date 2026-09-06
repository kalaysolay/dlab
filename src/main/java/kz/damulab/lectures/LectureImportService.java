package kz.damulab.lectures;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.content.Grade;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.Subject;
import kz.damulab.content.SubjectRepository;
import kz.damulab.content.Topic;
import kz.damulab.content.TopicRepository;

/**
 * Импортирует агентский JSON в обычные черновики лекций.
 *
 * <p>Сервис намеренно не скачивает URL и не читает пути на сервере: JSON endpoint
 * принимает изображения только как Base64, что исключает SSRF и доступ к локальной
 * файловой системе. Все изображения проходят существующую проверку сигнатуры, после
 * чего {@code asset://id} заменяется внутренним URL до общего HTML sanitation.</p>
 */
@Service
public class LectureImportService {

    private static final String SCHEMA_VERSION = "1.0";
    private static final String DOCUMENT_KIND = "damulab.lesson-import";
    private static final int MAX_BASE64_LENGTH = 14_000_000;
    private static final Pattern ASSET_URL = Pattern.compile("^asset://([a-z0-9][a-z0-9-]{0,63})$");

    private final SubjectRepository subjects;
    private final GradeRepository grades;
    private final TopicRepository topics;
    private final LectureRepository lectures;
    private final LectureService lectureService;
    private final LectureImageStorageService imageStorage;

    public LectureImportService(
            SubjectRepository subjects,
            GradeRepository grades,
            TopicRepository topics,
            LectureRepository lectures,
            LectureService lectureService,
            LectureImageStorageService imageStorage
    ) {
        this.subjects = subjects;
        this.grades = grades;
        this.topics = topics;
        this.lectures = lectures;
        this.lectureService = lectureService;
        this.imageStorage = imageStorage;
    }

    /**
     * Проверяет и импортирует весь batch атомарно: при ошибке не сохраняется ни одна
     * лекция, а уже записанные image assets удаляются из файлового хранилища.
     */
    @Transactional
    public LectureImportResponse importLessons(LectureImportRequest request) {
        validateEnvelope(request);
        List<String> storedKeys = new ArrayList<>();
        Set<String> externalIds = new HashSet<>();
        List<LectureImportResponse.ImportedLesson> imported = new ArrayList<>();
        try {
            for (LectureImportRequest.Lesson lesson : request.lessons()) {
                validateExternalId(lesson.externalId(), externalIds);
                Topic topic = resolveTopic(lesson.metadata());
                Map<String, StoredAsset> assets = storeAssets(lesson.assets(), storedKeys);
                Set<String> usedAssets = new HashSet<>();
                String ruHtml = prepareHtml(lesson.content().ruHtml(), assets, usedAssets);
                String kkHtml = prepareHtml(lesson.content().kkHtml(), assets, usedAssets);
                if (!usedAssets.equals(assets.keySet())) {
                    throw new LectureException("lecture_import_unused_asset");
                }

                LectureForm form = toLectureForm(lesson, topic, ruHtml, kkHtml);
                LectureResponse lecture = lectureService.createImportedLecture(form, lesson.externalId());
                imported.add(new LectureImportResponse.ImportedLesson(
                        lesson.externalId(),
                        lecture.id(),
                        lecture.status()
                ));
            }
            return new LectureImportResponse(imported.size(), List.copyOf(imported));
        } catch (RuntimeException ex) {
            // БД откатит транзакцию, но файловое хранилище не транзакционное.
            storedKeys.forEach(imageStorage::deleteIfExists);
            throw ex;
        }
    }

    private void validateEnvelope(LectureImportRequest request) {
        if (!SCHEMA_VERSION.equals(request.schemaVersion())) {
            throw new LectureException("lecture_import_schema_version_invalid");
        }
        if (!DOCUMENT_KIND.equals(request.kind())) {
            throw new LectureException("lecture_import_kind_invalid");
        }
    }

    private void validateExternalId(String externalId, Set<String> externalIds) {
        String normalized = externalId.toLowerCase(Locale.ROOT);
        if (!externalIds.add(normalized) || lectures.existsByExternalIdIgnoreCase(externalId)) {
            throw new LectureException("lecture_import_external_id_duplicate");
        }
    }

    /** Разрешает полный путь темы внутри строго выбранных предмета и класса. */
    private Topic resolveTopic(LectureImportRequest.Metadata metadata) {
        Subject subject = subjects.findByCodeIgnoreCase(metadata.subject().code())
                .orElseThrow(() -> new LectureException("lecture_import_subject_not_found"));
        if (!same(subject.getTitleRu(), metadata.subject().title().ru())
                || !same(subject.getTitleKk(), metadata.subject().title().kk())) {
            throw new LectureException("lecture_import_subject_title_mismatch");
        }
        Grade grade = grades.findByGradeNo(metadata.grade().number())
                .orElseThrow(() -> new LectureException("lecture_import_grade_not_found"));

        Topic current = null;
        for (String code : metadata.topic().path()) {
            Long parentId = current == null ? null : current.getId();
            current = topics.findByScopeAndCode(subject.getId(), grade.getId(), parentId, code)
                    .orElseThrow(() -> new LectureException("lecture_import_topic_not_found"));
        }
        if (!same(current.getTitleRu(), metadata.topic().title().ru())
                || !same(current.getTitleKk(), metadata.topic().title().kk())) {
            throw new LectureException("lecture_import_topic_title_mismatch");
        }
        return current;
    }

    /** Декодирует, проверяет и сохраняет каждый asset ровно один раз на лекцию. */
    private Map<String, StoredAsset> storeAssets(
            List<LectureImportRequest.Asset> requestedAssets,
            List<String> storedKeys
    ) {
        List<LectureImportRequest.Asset> safeAssets = requestedAssets == null ? List.of() : requestedAssets;
        Map<String, StoredAsset> result = new LinkedHashMap<>();
        for (LectureImportRequest.Asset asset : safeAssets) {
            if (result.containsKey(asset.id())) {
                throw new LectureException("lecture_import_asset_duplicate");
            }
            if (!"image".equals(asset.kind()) || !"base64".equals(asset.source().type())) {
                throw new LectureException("lecture_import_asset_source_invalid");
            }
            if (asset.source().data().length() > MAX_BASE64_LENGTH) {
                throw new LectureException("lecture_image_too_large");
            }

            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(asset.source().data().getBytes(StandardCharsets.US_ASCII));
            } catch (IllegalArgumentException ex) {
                throw new LectureException("lecture_import_asset_base64_invalid");
            }
            verifySha256(asset.sha256(), bytes);

            LectureImageStorageService.StoredLectureImage stored = imageStorage.store(bytes);
            storedKeys.add(stored.storageKey());
            if (!stored.contentType().equalsIgnoreCase(asset.mimeType())) {
                throw new LectureException("lecture_import_asset_mime_mismatch");
            }
            result.put(asset.id(), new StoredAsset(stored.storageKey(), stored.url()));
        }
        return result;
    }

    private void verifySha256(String expected, byte[] bytes) {
        if (expected == null || expected.isBlank()) {
            return;
        }
        String actual;
        try {
            actual = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 обязателен для любой Java 21 runtime; это ошибка окружения, а не входного JSON.
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
        if (!actual.equals(expected)) {
            throw new LectureException("lecture_import_asset_sha256_mismatch");
        }
    }

    /**
     * Разрешает image placeholders и затем применяет тот же sanitizer, что ручной
     * редактор. Формулы сравниваются до и после очистки, чтобы опасная или слишком
     * длинная формула не исчезла из лекции незаметно.
     */
    private String prepareHtml(String html, Map<String, StoredAsset> assets, Set<String> usedAssets) {
        Document input = Jsoup.parseBodyFragment(html);
        input.outputSettings().prettyPrint(false);
        for (Element image : input.select("img")) {
            java.util.regex.Matcher matcher = ASSET_URL.matcher(image.attr("src").trim());
            if (!matcher.matches()) {
                throw new LectureException("lecture_import_image_source_invalid");
            }
            String assetId = matcher.group(1);
            StoredAsset stored = assets.get(assetId);
            if (stored == null) {
                throw new LectureException("lecture_import_asset_not_found");
            }
            if (image.attr("alt").isBlank()) {
                throw new LectureException("lecture_import_image_alt_required");
            }
            image.attr("src", stored.url());
            usedAssets.add(assetId);
        }

        List<String> formulasBefore = normalizedFormulas(input);
        String sanitized = lectureService.sanitizeImportedRichText(input.body().html());
        Document output = Jsoup.parseBodyFragment(sanitized == null ? "" : sanitized);
        if (!formulasBefore.equals(normalizedFormulas(output))) {
            throw new LectureException("lecture_import_formula_invalid");
        }
        if (output.text().isBlank() && output.select("img, span.ql-formula").isEmpty()) {
            throw new LectureException("lecture_import_content_empty");
        }
        return sanitized;
    }

    private List<String> normalizedFormulas(Document document) {
        List<String> formulas = new ArrayList<>();
        for (Element formula : document.select("span.ql-formula")) {
            String value = formula.attr("data-value")
                    .trim()
                    .replace("\r\n", " ")
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .replaceAll("\\s{2,}", " ");
            formulas.add(value);
        }
        return formulas;
    }

    private LectureForm toLectureForm(
            LectureImportRequest.Lesson lesson,
            Topic topic,
            String ruHtml,
            String kkHtml
    ) {
        if (!"AUTO".equals(lesson.completionControl().mode())) {
            throw new LectureException("lecture_import_control_mode_invalid");
        }
        LectureForm form = new LectureForm();
        form.setTopicId(topic.getId());
        form.setTitleRu(lesson.metadata().title().ru());
        form.setTitleKk(lesson.metadata().title().kk());
        form.setContentRu(ruHtml);
        form.setContentKk(kkHtml);
        form.setSource(lesson.metadata().source());
        form.setControlMode(LectureControlMode.AUTO);
        form.setAutoCheckpointCount(lesson.completionControl().questionCount());
        form.setAttachments(lesson.attachments());
        return form;
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.trim().equals(right.trim());
    }

    private record StoredAsset(String storageKey, String url) {
    }
}
