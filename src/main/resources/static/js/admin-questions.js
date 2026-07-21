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
            default:
                return code ? ('Ошибка: ' + code) : 'Не удалось выполнить действие';
        }
    }

    async function runAction(button) {
        var row = button.closest('tr[data-question-id]');
        if (!row) {
            return;
        }
        var questionId = row.dataset.questionId;
        var action = button.dataset.action;
        if (!questionId || !action) {
            return;
        }

        var headers = {
            'Accept': 'application/json'
        };
        var token = csrfToken();
        if (token) {
            headers[csrfHeaderName()] = token;
        }

        var body = undefined;
        if (action === 'flag') {
            headers['Content-Type'] = 'application/json';
            body = JSON.stringify({ reason: button.dataset.reason || 'content_health' });
        }

        button.disabled = true;
        row.querySelectorAll('button[data-action]').forEach(function (btn) {
            btn.disabled = true;
        });

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
            applyQuestionToRow(row, payload);
            showToast('success', SUCCESS_TITLES[action] || 'Готово', 'Q-' + questionId);
        } catch (err) {
            showToast('error', 'Действие не выполнено', String(err.message || err));
            row.querySelectorAll('button[data-action]').forEach(function (btn) {
                btn.disabled = false;
            });
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
            var button = event.target.closest('button[data-action]');
            if (!button || !button.closest('.data-table')) {
                return;
            }
            event.preventDefault();
            runAction(button);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
