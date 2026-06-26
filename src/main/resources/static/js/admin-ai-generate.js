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

            var topicId = Number(document.getElementById('topicId') && document.getElementById('topicId').value);
            var questionType = document.getElementById('questionType') && document.getElementById('questionType').value;
            var difficulty = Number(document.getElementById('difficulty') && document.getElementById('difficulty').value);
            var count = Number(document.getElementById('count') && document.getElementById('count').value);
            var languageMode = document.getElementById('languageMode') && document.getElementById('languageMode').value;
            var instruction = document.getElementById('instruction') && document.getElementById('instruction').value;

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
                            throw new Error(data.error || 'generate_failed');
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
