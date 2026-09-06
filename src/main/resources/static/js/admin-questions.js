/**
 * Банк вопросов (/admin/questions): действия без перезагрузки и сохранение фильтров.
 *
 * Поток:
 * 1. При заходе на «голый» /admin/questions без subjectId/gradeId — восстанавливаем
 *    последние фильтры из sessionStorage (sidebar, старые POST-редиректы).
 * 2. Иначе синхронизируем адресную строку с текущей формой (history.replaceState)
 *    и сохраняем фильтры — F5 и «назад» из карточки не теряют выборку.
 * 3. Одобрить / Опубликовать / Архив / На review — POST /api/admin/questions/{id}/...,
 *    затем обновляем строку таблицы (или убираем, если больше не подходит под фильтр статуса).
 */
(function () {
    'use strict';

    var STORAGE_KEY = 'damulab.admin.questions.filters';
    var FILTER_KEYS = ['subjectId', 'gradeId', 'topicId', 'type', 'status', 'quality', 'query'];
    var activePreviewId = null;
    var previewReturnFocus = null;

    var SUCCESS_TITLES = {
        approve: 'Вопрос одобрен',
        publish: 'Вопрос опубликован',
        archive: 'Вопрос архивирован',
        flag: 'Вопрос отправлен на review'
    };

    function showToast(kind, title, body) {
        if (window.DamulabUi && typeof window.DamulabUi.showToast === 'function') {
            window.DamulabUi.showToast({ kind: kind, title: title, body: body || '' });
        }
    }

    function csrfToken() {
        var meta = document.querySelector('meta[name="_csrf"]');
        if (meta && meta.content) {
            return meta.content;
        }
        var input = document.querySelector('input[name="_csrf"]');
        return input ? input.value : '';
    }

    function csrfHeaderName() {
        var meta = document.querySelector('meta[name="_csrf_header"]');
        return meta && meta.content ? meta.content : 'X-CSRF-TOKEN';
    }

    function apiHeaders(withJsonBody) {
        var headers = { 'Accept': 'application/json' };
        var token = csrfToken();
        if (token) {
            headers[csrfHeaderName()] = token;
        }
        if (withJsonBody) {
            headers['Content-Type'] = 'application/json';
        }
        return headers;
    }

    function filterForm() {
        return document.querySelector('form.filter-bar');
    }

    /** Текущие значения фильтров из GET-формы списка. */
    function readFiltersFromForm() {
        var form = filterForm();
        var filters = {};
        FILTER_KEYS.forEach(function (key) {
            var el = form && form.elements ? form.elements[key] : null;
            filters[key] = el && typeof el.value === 'string' ? el.value : '';
        });
        return filters;
    }

    function readFiltersFromUrl() {
        var params = new URLSearchParams(window.location.search);
        var filters = {};
        FILTER_KEYS.forEach(function (key) {
            filters[key] = params.has(key) ? (params.get(key) || '') : '';
        });
        return filters;
    }

    function buildQuery(filters) {
        var params = new URLSearchParams();
        FILTER_KEYS.forEach(function (key) {
            var value = filters[key];
            if (value != null && String(value) !== '') {
                params.set(key, String(value));
            }
        });
        return params.toString();
    }

    function saveFilters(filters) {
        try {
            sessionStorage.setItem(STORAGE_KEY, JSON.stringify(filters));
        } catch (ignore) {
            // private mode / квота — фильтры всё равно живут в URL
        }
    }

    function loadSavedFilters() {
        try {
            var raw = sessionStorage.getItem(STORAGE_KEY);
            if (!raw) {
                return null;
            }
            var parsed = JSON.parse(raw);
            if (!parsed || typeof parsed !== 'object') {
                return null;
            }
            var filters = {};
            FILTER_KEYS.forEach(function (key) {
                filters[key] = parsed[key] == null ? '' : String(parsed[key]);
            });
            return filters;
        } catch (ignore) {
            return null;
        }
    }

    /**
     * Без subjectId/gradeId в query считаем URL «пустым» — типичный заход из sidebar
     * или старый redirect после Approve без query.
     */
    function urlMissingScope() {
        var params = new URLSearchParams(window.location.search);
        return !params.get('subjectId') || !params.get('gradeId');
    }

    /** Восстановить фильтры или записать текущие в URL/sessionStorage. */
    function persistFiltersOnLoad() {
        if (urlMissingScope()) {
            var saved = loadSavedFilters();
            if (saved && saved.subjectId && saved.gradeId) {
                var savedQuery = buildQuery(saved);
                if (savedQuery) {
                    window.location.replace('/admin/questions?' + savedQuery);
                    return true;
                }
            }
        }
        var filters = readFiltersFromForm();
        var query = buildQuery(filters);
        var nextUrl = query ? ('/admin/questions?' + query) : '/admin/questions';
        if (window.location.pathname + window.location.search !== nextUrl) {
            history.replaceState(null, '', nextUrl);
        }
        saveFilters(filters);
        return false;
    }

    function statusBadgeClass(status) {
        if (status === 'published' || status === 'approved') {
            return 'badge success';
        }
        if (status === 'archived') {
            return 'badge danger';
        }
        return 'badge warning';
    }

    /** Отрисовать формулы из сохранённого HTML мини-лекции после его вставки в DOM. */
    function renderMath(container) {
        if (!container || typeof window.renderMathInElement !== 'function') {
            return;
        }
        window.renderMathInElement(container, {
            delimiters: [
                { left: '$$', right: '$$', display: true },
                { left: '$', right: '$', display: false },
                { left: '\\[', right: '\\]', display: true },
                { left: '\\(', right: '\\)', display: false }
            ],
            throwOnError: false
        });
    }

    function currentStatusFilter() {
        var filters = readFiltersFromForm();
        return filters.status || '';
    }

    function matchesStatusFilter(status) {
        var selected = currentStatusFilter();
        if (!selected) {
            return true;
        }
        // В select value — имя enum (DRAFT), в API — apiValue (draft)
        return selected.toLowerCase() === String(status || '').toLowerCase();
    }

    function setMetric(name, value) {
        var el = document.querySelector('[data-metric="' + name + '"]');
        if (el) {
            el.textContent = String(Math.max(0, value));
        }
    }

    function readMetric(name) {
        var el = document.querySelector('[data-metric="' + name + '"]');
        if (!el) {
            return 0;
        }
        var n = Number(el.textContent);
        return Number.isFinite(n) ? n : 0;
    }

    function adjustMetrics(oldStatus, newStatus) {
        if (oldStatus === newStatus) {
            return;
        }
        if (oldStatus === 'draft') {
            setMetric('draft', readMetric('draft') - 1);
        }
        if (newStatus === 'draft') {
            setMetric('draft', readMetric('draft') + 1);
        }
        var oldReviewPub = oldStatus === 'needs_review' || oldStatus === 'published';
        var newReviewPub = newStatus === 'needs_review' || newStatus === 'published';
        if (oldReviewPub && !newReviewPub) {
            setMetric('reviewPublished', readMetric('reviewPublished') - 1);
        }
        if (!oldReviewPub && newReviewPub) {
            setMetric('reviewPublished', readMetric('reviewPublished') + 1);
        }
    }

    function removeRow(row) {
        var tbody = row.parentElement;
        row.remove();
        setMetric('total', readMetric('total') - 1);
        updateSelectionUi();
        if (tbody && !tbody.querySelector('tr')) {
            window.location.reload();
        }
    }

    function buildEditHref(questionId) {
        var query = buildQuery(readFiltersFromForm());
        return '/admin/questions/' + questionId + '/edit' + (query ? ('?' + query) : '');
    }

    function createActionButton(action, label, className, reason) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = className || 'button';
        button.textContent = label;
        button.dataset.action = action;
        if (reason) {
            button.dataset.reason = reason;
        }
        return button;
    }

    /** Пересобрать кнопки действий по статусу из API-ответа. */
    function renderActions(actionsCell, question) {
        var status = question.status;
        var pending = question.pendingDraftVersionNo;
        actionsCell.replaceChildren();

        var edit = document.createElement('a');
        edit.className = 'button';
        edit.href = buildEditHref(question.id);
        edit.textContent = 'Редактировать';
        actionsCell.appendChild(edit);

        if (status !== 'archived') {
            actionsCell.appendChild(createActionButton('flag', 'На review', 'button', 'content_health'));
        }
        if (status !== 'archived' && status !== 'approved' && status !== 'published') {
            actionsCell.appendChild(createActionButton('approve', 'Одобрить', 'button'));
        }
        if (status === 'approved' || (status === 'published' && pending != null)) {
            var publishLabel = status === 'published' ? 'Опубликовать черновик' : 'Опубликовать';
            actionsCell.appendChild(createActionButton('publish', publishLabel, 'button primary'));
        }
        if (status !== 'archived') {
            actionsCell.appendChild(createActionButton('archive', 'Архив', 'button danger'));
        }
    }

    function updateStatusCell(statusCell, question) {
        statusCell.replaceChildren();
        var badge = document.createElement('span');
        badge.className = statusBadgeClass(question.status);
        badge.textContent = question.status;
        statusCell.appendChild(badge);
        if (question.pendingDraftVersionNo != null) {
            var draft = document.createElement('span');
            draft.className = 'badge warning';
            draft.textContent = 'draft v' + question.pendingDraftVersionNo;
            statusCell.appendChild(draft);
        }
    }

    function applyQuestionToRow(row, question) {
        var oldStatus = row.dataset.status || '';
        row.dataset.status = question.status;
        row.dataset.pendingDraft = question.pendingDraftVersionNo == null
            ? ''
            : String(question.pendingDraftVersionNo);

        if (!matchesStatusFilter(question.status)) {
            adjustMetrics(oldStatus, question.status);
            removeRow(row);
            return;
        }

        var statusCell = row.querySelector('[data-col="status"]');
        var actionsCell = row.querySelector('[data-col="actions"]');
        if (statusCell) {
            updateStatusCell(statusCell, question);
        }
        if (actionsCell) {
            renderActions(actionsCell, question);
        }
        adjustMetrics(oldStatus, question.status);
    }

    function humanApiError(code) {
        switch (code) {
            case 'question_not_approved':
                return 'Сначала одобрите вопрос';
            case 'question_archived':
                return 'Вопрос уже в архиве';
            case 'question_not_found':
                return 'Вопрос не найден';
            case 'validation_failed':
                return 'Проверьте выбранные вопросы';
            case 'ai_provider_disabled':
                return 'Генерация лекций отключена в настройках AI';
            case 'openai_api_key_missing':
            case 'deepseek_api_key_missing':
                return 'Для генерации не настроен API-ключ';
            case 'openai_request_failed':
            case 'deepseek_request_failed':
                return 'AI-провайдер не ответил. Попробуйте ещё раз';
            default:
                return code ? ('Ошибка: ' + code) : 'Не удалось выполнить действие';
        }
    }

    function rowByQuestionId(questionId) {
        return document.querySelector('tr[data-question-id="' + String(questionId) + '"]');
    }

    function modal() {
        return document.getElementById('question-preview-modal');
    }

    function setPreviewBusy(isBusy) {
        var root = modal();
        if (!root) {
            return;
        }
        root.querySelectorAll('[data-preview-actions] button, [data-preview-generate-lecture]').forEach(function (button) {
            button.disabled = isBusy;
        });
    }

    function openPreview(questionId, opener) {
        var root = modal();
        if (!root) {
            return;
        }
        activePreviewId = String(questionId);
        previewReturnFocus = opener || document.activeElement;
        root.hidden = false;
        document.body.classList.add('question-preview-open');
        root.querySelector('[data-preview-content]').hidden = true;
        root.querySelector('[data-preview-error]').hidden = true;
        root.querySelector('[data-preview-loading]').hidden = false;
        root.querySelector('.question-preview-dialog').focus();
        loadPreview(questionId);
    }

    function closePreview() {
        var root = modal();
        if (!root || root.hidden) {
            return;
        }
        root.hidden = true;
        activePreviewId = null;
        document.body.classList.remove('question-preview-open');
        if (previewReturnFocus && document.contains(previewReturnFocus)) {
            previewReturnFocus.focus();
        }
        previewReturnFocus = null;
    }

    async function loadPreview(questionId) {
        var root = modal();
        try {
            var response = await fetch('/api/admin/questions/' + questionId + '/preview', {
                credentials: 'same-origin',
                headers: apiHeaders(false)
            });
            var payload = await response.json();
            if (!response.ok) {
                throw new Error(humanApiError(payload && payload.error));
            }
            if (activePreviewId !== String(questionId)) {
                return;
            }
            renderPreview(payload);
            root.querySelector('[data-preview-loading]').hidden = true;
            root.querySelector('[data-preview-content]').hidden = false;
        } catch (err) {
            if (activePreviewId !== String(questionId)) {
                return;
            }
            root.querySelector('[data-preview-loading]').hidden = true;
            var error = root.querySelector('[data-preview-error]');
            error.textContent = String(err.message || err);
            error.hidden = false;
        }
    }

    function setText(root, selector, value) {
        var node = root.querySelector(selector);
        if (node) {
            node.textContent = value == null ? '' : String(value);
        }
    }

    function appendAnswerText(parent, primary, secondary) {
        var main = document.createElement('span');
        main.textContent = primary || '—';
        parent.appendChild(main);
        if (secondary && secondary !== primary) {
            var translated = document.createElement('span');
            translated.className = 'muted-line';
            translated.textContent = secondary;
            parent.appendChild(translated);
        }
    }

    function renderChoiceAnswers(container, options) {
        (options || []).filter(function (option) {
            return option.textRu || option.textKk;
        }).forEach(function (option) {
            var item = document.createElement('div');
            item.className = 'question-preview-answer' + (option.correct ? ' is-correct' : '');
            var label = document.createElement('strong');
            label.className = 'question-preview-answer-label';
            label.textContent = option.label || '•';
            item.appendChild(label);
            var text = document.createElement('div');
            appendAnswerText(text, option.textRu, option.textKk);
            item.appendChild(text);
            if (option.correct) {
                var correct = document.createElement('span');
                correct.className = 'badge success';
                correct.textContent = 'Правильный';
                item.appendChild(correct);
            }
            container.appendChild(item);
        });
    }

    function renderMatchingAnswers(container, pairs) {
        (pairs || []).filter(function (pair) {
            return pair.leftRu || pair.leftKk || pair.rightRu || pair.rightKk;
        }).forEach(function (pair, index) {
            var item = document.createElement('div');
            item.className = 'question-preview-match';
            var left = document.createElement('div');
            appendAnswerText(left, pair.leftRu, pair.leftKk);
            var arrow = document.createElement('strong');
            arrow.textContent = '→';
            arrow.setAttribute('aria-label', 'соответствует');
            var right = document.createElement('div');
            appendAnswerText(right, pair.rightRu, pair.rightKk);
            var number = document.createElement('span');
            number.className = 'badge';
            number.textContent = String(index + 1);
            item.appendChild(number);
            item.appendChild(left);
            item.appendChild(arrow);
            item.appendChild(right);
            container.appendChild(item);
        });
    }

    function renderFillAnswers(container, answers) {
        (answers || []).filter(function (answer) {
            return answer.placeholder || answer.answer;
        }).forEach(function (answer) {
            var item = document.createElement('div');
            item.className = 'question-preview-answer is-correct';
            var placeholder = document.createElement('strong');
            placeholder.textContent = answer.placeholder || 'Поле';
            var value = document.createElement('span');
            value.textContent = answer.answer || '—';
            var mode = document.createElement('span');
            mode.className = 'badge success';
            mode.textContent = answer.matchMode + (answer.tolerance == null ? '' : ' ± ' + answer.tolerance);
            item.appendChild(placeholder);
            item.appendChild(value);
            item.appendChild(mode);
            container.appendChild(item);
        });
    }

    function renderPreviewAnswers(root, content) {
        var container = root.querySelector('[data-preview-answers]');
        container.replaceChildren();
        if (content.type === 'SCQ' || content.type === 'MCQ') {
            renderChoiceAnswers(container, content.options);
        } else if (content.type === 'MATCHING') {
            renderMatchingAnswers(container, content.matchingPairs);
        } else if (content.type === 'FILL_IN') {
            renderFillAnswers(container, content.fillAnswers);
        }
        if (!container.children.length) {
            var empty = document.createElement('span');
            empty.className = 'muted-line';
            empty.textContent = 'Ответы не заданы';
            container.appendChild(empty);
        }
    }

    function renderPreviewActions(root, question) {
        var container = root.querySelector('[data-preview-actions]');
        container.replaceChildren();
        if (question.status !== 'archived') {
            container.appendChild(createActionButton('flag', 'На review', 'button', 'content_health'));
        }
        if (question.status !== 'archived' && question.status !== 'approved' && question.status !== 'published') {
            container.appendChild(createActionButton('approve', 'Одобрить', 'button'));
        }
        if (question.status === 'approved' || (question.status === 'published' && question.pendingDraftVersionNo != null)) {
            var label = question.status === 'published' ? 'Опубликовать черновик' : 'Опубликовать';
            container.appendChild(createActionButton('publish', label, 'button primary'));
        }
        if (question.status !== 'archived') {
            container.appendChild(createActionButton('archive', 'Архив', 'button danger'));
        }
        container.querySelectorAll('[data-action]').forEach(function (button) {
            button.dataset.questionId = question.id;
        });
    }

    function renderLecture(root, preview) {
        var ru = root.querySelector('[data-preview-lecture-ru]');
        var kk = root.querySelector('[data-preview-lecture-kk]');
        ru.innerHTML = preview.miniLectureRuHtml || '';
        kk.innerHTML = preview.miniLectureKkHtml || '';
        renderMath(ru);
        renderMath(kk);
        var hasLecture = Boolean(preview.miniLectureRuHtml || preview.miniLectureKkHtml);
        root.querySelector('[data-preview-lecture-empty]').hidden = hasLecture;
        root.querySelector('.question-preview-lecture-tabs').hidden = !hasLecture;
        ru.hidden = !hasLecture;
        kk.hidden = true;
        root.querySelectorAll('[data-lecture-lang]').forEach(function (button) {
            var active = button.dataset.lectureLang === 'ru';
            button.classList.toggle('active', active);
            button.setAttribute('aria-selected', active ? 'true' : 'false');
        });
    }

    function renderPreview(preview) {
        var root = modal();
        var question = preview.question;
        var content = preview.content;
        activePreviewId = String(question.id);
        setText(root, '[data-preview-id]', 'Q-' + question.id);
        setText(root, '[data-preview-status]', question.status);
        setText(root, '[data-preview-version]', 'v' + preview.previewVersionNo + (preview.pendingDraft ? ' · черновик' : ''));
        root.querySelector('[data-preview-status]').className = statusBadgeClass(question.status);
        root.querySelector('[data-preview-body-ru]').innerHTML = preview.bodyRuHtml || '';
        root.querySelector('[data-preview-body-kk]').innerHTML = preview.bodyKkHtml || '';
        setText(root, '[data-preview-meta]', [content.type, 'сложность ' + content.difficulty + '/5', question.primaryTopicTitleRu || 'Без темы', content.source].join(' · '));
        root.querySelector('[data-preview-edit]').href = buildEditHref(question.id);
        root.querySelector('[data-preview-generate-lecture]').disabled = question.status === 'archived';
        renderPreviewAnswers(root, content);
        renderLecture(root, preview);
        renderPreviewActions(root, question);
    }

    function switchLectureLanguage(language) {
        var root = modal();
        root.querySelector('[data-preview-lecture-ru]').hidden = language !== 'ru';
        root.querySelector('[data-preview-lecture-kk]').hidden = language !== 'kk';
        root.querySelectorAll('[data-lecture-lang]').forEach(function (button) {
            var active = button.dataset.lectureLang === language;
            button.classList.toggle('active', active);
            button.setAttribute('aria-selected', active ? 'true' : 'false');
        });
    }

    async function generatePreviewLecture(button) {
        if (!activePreviewId) {
            return;
        }
        var original = button.textContent;
        setPreviewBusy(true);
        button.textContent = 'Генерируем…';
        try {
            var response = await fetch('/api/admin/questions/' + activePreviewId + '/mini-lecture/generate', {
                method: 'POST',
                credentials: 'same-origin',
                headers: apiHeaders(false)
            });
            var payload = await response.json();
            if (!response.ok) {
                throw new Error(humanApiError(payload && payload.error));
            }
            var row = rowByQuestionId(activePreviewId);
            if (row) {
                applyQuestionToRow(row, payload.preview.question);
            }
            renderPreview(payload.preview);
            showToast('success', 'Мини-лекция создана', payload.stubMode ? 'Использован Stub-провайдер' : 'Лекция сохранена в вопросе');
        } catch (err) {
            showToast('error', 'Лекция не создана', String(err.message || err));
        } finally {
            button.textContent = original;
            setPreviewBusy(false);
        }
    }

    function selectedRows() {
        return Array.from(document.querySelectorAll('tr[data-question-id]')).filter(function (row) {
            var checkbox = row.querySelector('[data-question-select]');
            return checkbox && checkbox.checked;
        });
    }

    function updateSelectionUi() {
        var selected = selectedRows();
        var all = Array.from(document.querySelectorAll('tr[data-question-id] [data-question-select]'));
        var bar = document.querySelector('[data-bulk-bar]');
        var count = document.querySelector('[data-bulk-count]');
        var selectAll = document.querySelector('[data-select-all]');
        if (bar) {
            bar.hidden = selected.length === 0;
        }
        if (count) {
            count.textContent = String(selected.length);
        }
        if (selectAll) {
            selectAll.checked = all.length > 0 && selected.length === all.length;
            selectAll.indeterminate = selected.length > 0 && selected.length < all.length;
        }
    }

    function clearSelection() {
        document.querySelectorAll('[data-question-select], [data-select-all]').forEach(function (checkbox) {
            checkbox.checked = false;
            checkbox.indeterminate = false;
        });
        updateSelectionUi();
    }

    async function runBulkAction(button) {
        var rows = selectedRows();
        if (!rows.length) {
            return;
        }
        var action = button.dataset.bulkAction;
        var ids = rows.map(function (row) { return Number(row.dataset.questionId); });
        document.querySelectorAll('[data-bulk-action], [data-bulk-clear]').forEach(function (item) {
            item.disabled = true;
        });
        try {
            var response = await fetch('/api/admin/questions/bulk', {
                method: 'POST',
                credentials: 'same-origin',
                headers: apiHeaders(true),
                body: JSON.stringify({ questionIds: ids, action: String(action).toUpperCase() })
            });
            var payload = await response.json();
            if (!response.ok) {
                throw new Error(humanApiError(payload && payload.error));
            }
            payload.items.forEach(function (item) {
                if (!item.question) {
                    return;
                }
                var row = rowByQuestionId(item.questionId);
                if (row) {
                    var checkbox = row.querySelector('[data-question-select]');
                    if (checkbox) {
                        checkbox.checked = false;
                    }
                    applyQuestionToRow(row, item.question);
                }
            });
            var title = action === 'publish' ? 'Публикация завершена' : 'Одобрение завершено';
            var body = payload.succeeded + ' успешно';
            if (payload.failed) {
                body += ', ' + payload.failed + ' с ошибкой';
            }
            showToast(payload.failed ? 'warning' : 'success', title, body);
        } catch (err) {
            showToast('error', 'Пакетное действие не выполнено', String(err.message || err));
        } finally {
            document.querySelectorAll('[data-bulk-action], [data-bulk-clear]').forEach(function (item) {
                item.disabled = false;
            });
            updateSelectionUi();
        }
    }

    async function runAction(button) {
        var row = button.closest('tr[data-question-id]');
        var questionId = row ? row.dataset.questionId : button.dataset.questionId;
        var action = button.dataset.action;
        if (!questionId || !action) {
            return;
        }
        // Действия из модалки не вложены в строку таблицы. Находим строку по ID,
        // чтобы тот же API-ответ сразу обновлял статус и доступные действия в списке.
        row = row || rowByQuestionId(questionId);

        var headers = apiHeaders(action === 'flag');
        var body = undefined;
        if (action === 'flag') {
            body = JSON.stringify({ reason: button.dataset.reason || 'content_health' });
        }

        button.disabled = true;
        if (row) {
            row.querySelectorAll('button[data-action]').forEach(function (btn) {
                btn.disabled = true;
            });
        }
        if (activePreviewId === String(questionId)) {
            setPreviewBusy(true);
        }

        try {
            var response = await fetch('/api/admin/questions/' + questionId + '/' + action, {
                method: 'POST',
                credentials: 'same-origin',
                headers: headers,
                body: body
            });
            var payload = null;
            try {
                payload = await response.json();
            } catch (ignore) {
                payload = null;
            }
            if (!response.ok) {
                var code = payload && payload.error ? payload.error : ('http_' + response.status);
                throw new Error(humanApiError(code));
            }
            if (row) {
                applyQuestionToRow(row, payload);
            }
            if (activePreviewId === String(questionId)) {
                if (action === 'archive') {
                    closePreview();
                } else {
                    await loadPreview(questionId);
                }
            }
            showToast('success', SUCCESS_TITLES[action] || 'Готово', 'Q-' + questionId);
        } catch (err) {
            showToast('error', 'Действие не выполнено', String(err.message || err));
            if (row) {
                row.querySelectorAll('button[data-action]').forEach(function (btn) {
                    btn.disabled = false;
                });
            }
        } finally {
            if (activePreviewId === String(questionId)) {
                setPreviewBusy(false);
            }
        }
    }

    function onFilterSubmit() {
        // GET-submit сам положит params в URL; дублируем в storage до ухода со страницы
        saveFilters(readFiltersFromForm());
    }

    function init() {
        if (persistFiltersOnLoad()) {
            return;
        }

        var form = filterForm();
        if (form) {
            form.addEventListener('submit', onFilterSubmit);
        }

        document.addEventListener('click', function (event) {
            var previewOpen = event.target.closest('[data-preview-open]');
            if (previewOpen) {
                var previewRow = previewOpen.closest('tr[data-question-id]');
                if (previewRow) {
                    event.preventDefault();
                    openPreview(previewRow.dataset.questionId, previewOpen);
                }
                return;
            }

            if (event.target.closest('[data-preview-close]')) {
                event.preventDefault();
                closePreview();
                return;
            }

            var lectureTab = event.target.closest('[data-lecture-lang]');
            if (lectureTab) {
                switchLectureLanguage(lectureTab.dataset.lectureLang);
                return;
            }

            var generateLecture = event.target.closest('[data-preview-generate-lecture]');
            if (generateLecture) {
                generatePreviewLecture(generateLecture);
                return;
            }

            var bulkAction = event.target.closest('[data-bulk-action]');
            if (bulkAction) {
                runBulkAction(bulkAction);
                return;
            }

            if (event.target.closest('[data-bulk-clear]')) {
                clearSelection();
                return;
            }

            var button = event.target.closest('button[data-action]');
            if (!button) {
                return;
            }
            event.preventDefault();
            runAction(button);
        });

        document.addEventListener('change', function (event) {
            if (event.target.matches('[data-select-all]')) {
                document.querySelectorAll('[data-question-select]').forEach(function (checkbox) {
                    checkbox.checked = event.target.checked;
                });
                updateSelectionUi();
                return;
            }
            if (event.target.matches('[data-question-select]')) {
                updateSelectionUi();
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && activePreviewId) {
                closePreview();
            }
        });

        // При возврате со страницы редактирования браузер может восстановить список из
        // back/forward cache вместе со старым DOM. Запрашиваем страницу заново, чтобы
        // статус и набор действий соответствовали уже сохранённому состоянию вопроса.
        window.addEventListener('pageshow', function (event) {
            if (event.persisted) {
                window.location.reload();
            }
        });

        updateSelectionUi();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
