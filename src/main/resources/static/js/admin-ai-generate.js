/**
 * Асинхронная генерация вопросов через ИИ.
 *
 * Поток работы:
 * 1. Перехватываем submit формы, отправляем POST /api/admin/ai/questions/generate.
 * 2. Сервер сразу возвращает job в статусе pending — показываем лоадер и toast «Запущено».
 * 3. Polling раз в 2 секунды до терминального статуса (succeeded | failed).
 * 4. При завершении — toast с результатом и полная перезагрузка страницы (сервер отрисует batch).
 *
 * При загрузке страницы: если jobId задан в window.aiGenJobId и статус pending/running,
 * polling стартует автоматически (перезагрузка не теряет задачу).
 */
(function () {
    'use strict';

    var POLL_INTERVAL_MS = 2000;
    var POLL_MAX_ATTEMPTS = 150; // 5 минут

    // ───── Утилиты ─────────────────────────────────────────────────────────────

    function showToast(kind, title, body) {
        if (window.DamulabUi && typeof window.DamulabUi.showToast === 'function') {
            window.DamulabUi.showToast({ kind: kind, title: title, body: body || '' });
        }
    }

    function csrf() {
        var el = document.querySelector('input[name="_csrf"]');
        return el ? el.value : '';
    }

    /** Значение поля формы по name или id (пустая строка → ''). */
    function readFormValue(form, name) {
        var el = (form && form.elements && form.elements[name])
                || (form && form.querySelector('[name="' + name + '"]'))
                || document.getElementById(name);
        // RadioNodeList: берём value выбранного; для select/input — .value
        if (!el) {
            return '';
        }
        if (typeof el.value === 'string') {
            return el.value;
        }
        return '';
    }

    /** Целое из поля формы; при пустом/NaN — fallback (как дефолты AiQuestionGenerationForm). */
    function readFormInt(form, name, fallback) {
        var raw = readFormValue(form, name);
        if (raw === '' || raw == null) {
            return fallback;
        }
        var n = Number(raw);
        return Number.isFinite(n) ? n : fallback;
    }

    function setProgressVisible(visible) {
        var panel = document.getElementById('ai-gen-progress');
        if (panel) {
            panel.style.display = visible ? 'flex' : 'none';
        }
    }

    function setSubmitDisabled(disabled) {
        var btn = document.getElementById('ai-gen-submit');
        if (btn) {
            btn.disabled = disabled;
        }
    }

    // ───── Polling ──────────────────────────────────────────────────────────────

    /**
     * Запрашивает статус job каждые 2 секунды.
     * При succeeded/failed — toast + перезагрузка страницы (сервер отрисует batch).
     */
    function startPolling(jobId) {
        setProgressVisible(true);
        setSubmitDisabled(true);

        var attempts = 0;

        function poll() {
            attempts++;
            if (attempts > POLL_MAX_ATTEMPTS) {
                showToast('warning', 'Генерация занимает больше времени, чем ожидалось', 'Обновите страницу вручную');
                setProgressVisible(false);
                setSubmitDisabled(false);
                return;
            }
            fetch('/api/admin/ai/jobs/' + jobId, { credentials: 'same-origin' })
                .then(function (res) { return res.json(); })
                .then(function (job) {
                    var status = job.status;
                    updateProgressText(job);
                    if (status === 'succeeded') {
                        var count = (job.items || []).length;
                        showToast('success', 'Генерация завершена', 'Создано черновиков: ' + count + ' — AI-' + job.id);
                        setProgressVisible(false);
                        window.location.reload();
                    } else if (status === 'failed') {
                        showToast('error', 'Ошибка генерации', job.errorCode || 'unknown_error');
                        setProgressVisible(false);
                        setSubmitDisabled(false);
                        window.location.reload();
                    } else {
                        window.setTimeout(poll, POLL_INTERVAL_MS);
                    }
                })
                .catch(function () {
                    window.setTimeout(poll, POLL_INTERVAL_MS);
                });
        }

        poll();
    }

    function updateProgressText(job) {
        var el = document.getElementById('ai-gen-progress-text');
        if (!el) { return; }
        var statusLabel = { pending: 'Ожидание...', running: 'Генерируем вопросы...' }[job.status] || job.status;
        el.textContent = statusLabel + ' (AI-' + job.id + ')';
    }

    // ───── Submit формы ─────────────────────────────────────────────────────────

    function initForm() {
        var form = document.getElementById('ai-gen-form');
        if (!form) { return; }

        form.addEventListener('submit', function (evt) {
            evt.preventDefault();

            // topicId — checked radio в дереве (name=topicId). Остальные поля — по name/id формы.
            // Defaults совпадают с AiQuestionGenerationForm, чтобы null/NaN не ломали @Valid.
            var checkedTopic = form.querySelector('input[name="topicId"]:checked');
            var topicId = checkedTopic ? Number(checkedTopic.value) : 0;
            var questionType = readFormValue(form, 'questionType') || 'SCQ';
            var difficulty = readFormInt(form, 'difficulty', 2);
            var count = readFormInt(form, 'count', 5);
            var languageMode = readFormValue(form, 'languageMode') || 'RU_KK';
            var instruction = readFormValue(form, 'instruction');

            if (!topicId) {
                showToast('warning', 'Выберите тему', 'Тема обязательна для генерации');
                return;
            }

            var payload = {
                topicId: topicId,
                questionType: questionType,
                difficulty: difficulty,
                count: count,
                languageMode: languageMode,
                instruction: instruction || null
            };

            // Few-shot эталоны: если пикер отрисован (у темы есть активные эталоны),
            // передаём отмеченные id. Пустой массив => не слать ни одного; отсутствие
            // чекбоксов (пикер не загружен) => поле не отправляем => сервер возьмёт все активные.
            var exampleBoxes = form.querySelectorAll('#ai-examples-picker input[name="exampleIds"]');
            if (exampleBoxes.length) {
                payload.exampleIds = Array.prototype.slice
                        .call(form.querySelectorAll('#ai-examples-picker input[name="exampleIds"]:checked'))
                        .map(function (cb) { return Number(cb.value); });
            }

            setSubmitDisabled(true);
            setProgressVisible(true);

            fetch('/api/admin/ai/questions/generate', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-TOKEN': csrf()
                },
                body: JSON.stringify(payload)
            })
                .then(function (res) {
                    if (!res.ok) {
                        return res.json().then(function (data) {
                            throw new Error(data.message || data.error || 'generate_failed');
                        });
                    }
                    return res.json();
                })
                .then(function (job) {
                    // Обновляем URL без перезагрузки — перезагрузка с ?jobId= продолжит polling
                    var subjectId = document.getElementById('ai-subject') && document.getElementById('ai-subject').value;
                    var gradeId = document.getElementById('ai-grade') && document.getElementById('ai-grade').value;
                    var params = new URLSearchParams({ jobId: job.id });
                    if (subjectId) { params.set('subjectId', subjectId); }
                    if (gradeId) { params.set('gradeId', gradeId); }
                    history.replaceState(null, '', '/admin/questions/ai-generate?' + params.toString());
                    showToast('info', 'Генерация запущена', 'AI-' + job.id + ' — ожидайте результат');
                    startPolling(job.id);
                })
                .catch(function (err) {
                    setProgressVisible(false);
                    setSubmitDisabled(false);
                    showToast('error', 'Не удалось запустить генерацию', String(err.message || err));
                });
        });
    }

    // ───── Retry через fetch ────────────────────────────────────────────────────

    function initRetryButtons() {
        document.querySelectorAll('[data-ai-retry-job]').forEach(function (btn) {
            btn.addEventListener('click', function (evt) {
                evt.preventDefault();
                var jobId = btn.getAttribute('data-ai-retry-job');
                var csrfToken = csrf();
                btn.disabled = true;

                fetch('/api/admin/ai/jobs/' + jobId + '/retry', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'X-CSRF-TOKEN': csrfToken }
                })
                    .then(function (res) {
                        if (!res.ok) {
                            return res.json().then(function (d) { throw new Error(d.error || 'retry_failed'); });
                        }
                        return res.json();
                    })
                    .then(function (job) {
                        showToast('info', 'Повторный запуск', 'AI-' + job.id + ' — ожидайте результат');
                        startPolling(job.id);
                    })
                    .catch(function (err) {
                        btn.disabled = false;
                        showToast('error', 'Не удалось повторить запрос', String(err.message || err));
                    });
            });
        });
    }

    // ───── Инициализация ────────────────────────────────────────────────────────

    document.addEventListener('DOMContentLoaded', function () {
        initForm();
        initRetryButtons();

        // Автостарт polling при перезагрузке с pending/running job
        if (window.aiGenJobId && window.aiGenJobStatus &&
                (window.aiGenJobStatus === 'pending' || window.aiGenJobStatus === 'running')) {
            startPolling(window.aiGenJobId);
        }
    });
})();
