/* Новый клиент: HTTP — источник состояния, STOMP — сигнал перечитать его. Банки/ключи и будущие условия не загружаются. */
(function () {
    "use strict";
    const labels = window.quizLabels || {}, core = window.QuizGameCore;
    const byId = id => document.getElementById(id);
    const text = (id, value) => { const el = byId(id); if (el && el.textContent !== String(value ?? "")) el.textContent = value ?? ""; };
    const show = (id, visible) => { const el = byId(id); if (el) el.hidden = !visible; };
    const node = (tag, value, className) => { const el = document.createElement(tag); if (value != null) el.textContent = value; if (className) el.className = className; return el; };
    const error = message => { text("qg-error", message); byId("qg-error")?.focus(); };
    const status = (message, focus) => { text("qg-status", message); if (focus) byId("qg-status")?.focus(); };
    async function api(url, payload) {
        const headers = { Accept: "application/json" };
        if (payload !== undefined) {
            headers[document.querySelector('meta[name="_csrf_header"]').content] = document.querySelector('meta[name="_csrf"]').content;
            headers["Content-Type"] = "application/json";
        }
        const response = await fetch(url, { method: payload === undefined ? "GET" : "POST", credentials: "same-origin", headers,
            body: payload === undefined ? undefined : JSON.stringify(payload), redirect: "error" });
        const json = response.status === 204 ? null : await response.json().catch(() => null);
        if (!response.ok) { const ex = new Error(json?.message || labels.requestError); ex.code = json?.code; ex.status = response.status; throw ex; }
        return json;
    }
    // Карточка сохраняет тот же form key при повторной отправке после сетевого сбоя.
    document.querySelectorAll("[data-create-game]").forEach(form => form.addEventListener("submit", () => {
        form.querySelector('button[type="submit"]').disabled = true;
    }));
    window.addEventListener("pageshow",event=>{ if(event.persisted) window.location.reload(); });
    const join = document.querySelector("[data-quiz-join]");
    if (join) {
        let code, pending = false;
        const quickJoin = join.hasAttribute("data-quick-join"), enter = byId("qg-enter");
        const joinRoom = async () => {
            const joined = await api(`/api/quiz/rooms/${code}/join`, {});
            window.location.assign(`/student/quiz/rooms/${joined.code}`);
        };
        byId("qg-code-form").addEventListener("submit", async event => {
            event.preventDefault(); if (pending) return; pending = true; show("qg-preview", false); text("qg-error", "");
            try {
                code = core.normalizeCode(byId("qg-code").value); byId("qg-code").value = code;
                const preview = await api(`/api/quiz/rooms/${code}/preview`);
                text("qg-preview-title", preview.gameTitle); text("qg-preview-meta", `${labels.organizer}: ${preview.organizer} · ${preview.participantCount}/${preview.maxPlayers} ${labels.players}`);
                text("qg-preview-blocker", labels[preview.joinBlocker] || "");
                let existing = false;
                try { await api(`/api/quiz/rooms/${code}`); existing = true; } catch (ex) { if (ex.status !== 403) throw ex; }
                if (quickJoin && (preview.canJoin || existing)) { await joinRoom(); return; }
                if (enter) enter.disabled = !preview.canJoin && !existing;
                show("qg-preview", true); byId("qg-preview-title").focus();
            } catch (ex) { error(labels[ex.message] || ex.message || labels.requestError); }
            finally { pending = false; }
        });
        // Любое редактирование кода инвалидирует прежнее preview; нельзя войти не в показанную комнату.
        byId("qg-code").addEventListener("input", () => { code = null; show("qg-preview", false); });
        enter?.addEventListener("click", async () => {
            if (pending || !code) return; pending = true; byId("qg-enter").disabled = true;
            try { await joinRoom(); }
            catch (ex) { error(ex.message); pending = false; byId("qg-enter").disabled = false; }
        });
    }
    const root = document.querySelector("[data-quiz-game-room]");
    if (!root) return;
    const base = `/api/quiz/rooms/${root.dataset.code}`;
    let room, round, offset = 0, pending = false, polling = false, socket, stopped = false, reconnect, pollTimer, realtimeConnected = false, resultLoaded = false;
    let previousSeconds = Infinity, announced = new Set(), dirtySettings = false, seenRevision, lostPayload, timeoutRefreshRoundId;
    const FALLBACK_POLL_MS = 10000;
    // При нормальной работе сети ничего не показываем: техническое «состояние обновлено»
    // отвлекает ребёнка. Сообщение появляется только тогда, когда действительно есть проблема.
    function online(connected) { text("qg-network", connected ? "" : labels.connection); }
    async function refresh() {
        if (polling || pending || stopped) return; polling = true;
        try { apply(await api(base)); online(true); }
        catch (ex) { online(false); if (ex.status === 403 || ex.status === 404) { stopped = true; socket?.close(); error(ex.message); } }
        finally { polling = false; }
    }
    async function pollFallback() {
        if (stopped || realtimeConnected) return;
        await refresh(); scheduleFallbackPoll();
    }
    function scheduleFallbackPoll(delay = FALLBACK_POLL_MS) {
        clearTimeout(pollTimer);
        if (stopped || realtimeConnected) return;
        pollTimer = setTimeout(pollFallback, delay);
    }
    function apply(next) {
        if (room && Date.parse(next.serverTime) < Date.parse(room.serverTime)) return;
        const previousStatus = room?.status;
        room = next; offset = Date.parse(room.serverTime) - Date.now();
        show("qg-lobby", room.status === "waiting"); show("qg-active", room.status === "active");
        show("qg-terminal", ["cancelled", "expired"].includes(room.status)); show("qg-results", room.status === "finished");
        const waiting = room.status === "waiting", host = room.currentParticipantHost;
        if (waiting) {
            status(labels.waiting, false);
            text("qg-settings-summary", `${labels.rounds}: ${room.questionCount} · ${labels.seconds}: ${room.roundSeconds}${room.gradeTitle ? ` · ${room.gradeTitle}` : ""}`);
            const list = byId("qg-participants"); list.replaceChildren();
            room.participants.forEach(p => {
                const item = node("div", null, "qg-participant");
                const shownName = p.current ? labels.you : p.displayName;
                const avatar = node("span", shownName.trim().charAt(0).toUpperCase(), "qg-participant-avatar");
                const copy = node("span", null, "qg-participant-copy");
                copy.append(node("strong", shownName), node("small", p.host ? (p.current ? labels.hostYou : labels.host) : (p.current ? labels.joinedYou : labels.player)));
                item.append(avatar, copy, node("span", p.ready ? labels.ready : labels.waiting, `qg-participant-state${p.ready ? " is-ready" : ""}`));
                list.append(item);
            });
            const blockers = byId("qg-blockers"); blockers.replaceChildren(...room.startBlockers.map(b => node("li", labels[b] || labels.requestError)));
            show("qg-start", host); byId("qg-start").disabled = pending || (!room.canStart && !dirtySettings);
            show("qg-ready", !host); byId("qg-ready").disabled = room.participants.find(p => p.current)?.ready || pending;
            show("qg-invite", host && room.canInvite); show("qg-cancel", host); show("qg-leave", !host);
            show("qg-settings", host && (room.gradeSelectionMode !== "NONE" || room.questionCountEditable || room.roundSecondsEditable)); show("qg-grade-field", room.gradeSelectionMode !== "NONE");
            // Poll не затирает несохранённый ввод; новая revision требует заново подтвердить правила.
            if (!dirtySettings || seenRevision !== room.settingsRevision) {
                byId("qg-grade").value = room.gradeId || ""; byId("qg-count").value = room.questionCount; byId("qg-seconds").value = room.roundSeconds;
                dirtySettings = false; seenRevision = room.settingsRevision;
            }
            for (const [id, editable, min, max] of [["qg-count",room.questionCountEditable,room.minQuestionCount,room.maxQuestionCount], ["qg-seconds",room.roundSecondsEditable,room.minRoundSeconds,room.maxRoundSeconds]]) {
                byId(id).disabled = !editable; byId(id).min = min; byId(id).max = max;
            }
            byId("qg-grade").disabled = room.gradeSelectionMode === "NONE";
            byId("qg-grade").required = room.gradeSelectionMode !== "NONE";
            return;
        }
        if (room.status === "active") renderRound(room.currentRound);
        if (["cancelled", "expired"].includes(room.status)) {
            text("qg-terminal-title", labels[room.status]); status(labels[room.status], false);
            // После скрытия прежних действий фокус остаётся на понятном завершении сценария.
            if (previousStatus !== room.status) byId("qg-terminal-title").focus();
            stopped = true; socket?.close();
        }
        if (room.status === "finished") {
            stopped = true; socket?.close();
            if (root.dataset.initialStatus !== "finished") { window.location.replace(`/student/quiz/rooms/${room.code}/results`); return; }
            loadResults();
        }
    }
    function renderRound(current) {
        if (!current) { round=null; byId("qg-options").replaceChildren(); lockAnswer(true); text("qg-question",labels.next); text("qg-timer",""); status(labels.next, false); return; }
        if (round?.id !== current.id) {
            round = current; lostPayload = null; previousSeconds = Infinity; timeoutRefreshRoundId = null;
            try { announced = new Set(JSON.parse(sessionStorage.getItem(`quiz-thresholds:${room.code}:${round.id}`) || "[]")); } catch { announced = new Set(); }
            text("qg-progress", `${labels.round} ${current.orderNo}/${room.questionCount}`); text("qg-question", current.prompt);
            byId("qg-question").lang = current.presentation?.sourceLanguage || current.locale;
            text("qg-direction", current.presentation?.sourceLanguage ? `${current.presentation.sourceLanguage.toUpperCase()} → ${current.presentation.targetLanguage.toUpperCase()}` : labels[room.gameType]);
            const options = byId("qg-options"); options.replaceChildren(); text("qg-error", "");
            let mode = current.format;
            if (current.contentKind === "FACT") {
                text("qg-answer-legend", labels.select); addChoice("true", labels.true, "radio"); addChoice("false", labels.false, "radio");
            } else if (["SCQ", "MCQ"].includes(mode) || current.contentKind === "VOCABULARY") {
                text("qg-answer-legend", mode === "MCQ" ? labels.multiple : labels.select);
                current.options.forEach(o => addChoice(o.id, o.text, mode === "MCQ" ? "checkbox" : "radio"));
                options.lang = current.presentation?.targetLanguage || current.locale;
            } else if (mode === "MATCHING") {
                text("qg-answer-legend", labels.matching);
                current.options.left.forEach((left, i) => {
                    const label = node("label", left.text, "field"), select = node("select"); select.dataset.answerKey = left.id; select.id = `qg-match-${i}`;
                    select.append(new Option("—", ""));
                    current.options.right.forEach(r => select.append(new Option(r.text, r.id)));
                    select.required = true; select.setAttribute("aria-describedby", "qg-error"); label.append(select); options.append(label);
                });
            } else if (mode === "FILL_IN") {
                text("qg-answer-legend", labels.fill);
                current.options.placeholders.forEach((key, i) => {
                    const label = node("label", key.includes("[[") ? key : `[[${key}]]`, "field"), input = node("input"); input.type = "text"; input.dataset.answerKey = key; input.id = `qg-fill-${i}`;
                    input.required = true; input.autocomplete = "off"; input.setAttribute("aria-describedby", "qg-error"); label.append(input); options.append(label);
                });
            }
            byId("qg-question").focus();
        } else round = current;
        lockAnswer(current.answered || pending);
        status(current.answered ? labels.accepted : lostPayload ? labels.uncertain : labels[room.gameType], false);
        tick();
    }
    function addChoice(value, label, type) {
        const row = node("label", null, "qg-choice"), input = node("input"); input.type = type; input.name = "answer"; input.value = value;
        input.setAttribute("aria-describedby", "qg-error"); row.append(input, node("span", label)); byId("qg-options").append(row);
    }
    function lockAnswer(locked) { byId("qg-answer-fields").disabled = locked || !!lostPayload; byId("qg-submit").disabled = locked; byId("qg-skip").disabled = locked || !!lostPayload; }
    function currentAnswer() {
        if (round.contentKind === "FACT") {
            const chosen = byId("qg-options").querySelector("input:checked"); return core.answer(round, chosen ? chosen.value === "true" : undefined);
        }
        if (["SCQ", "MCQ"].includes(round.format) || round.contentKind === "VOCABULARY") return core.answer(round, Array.from(byId("qg-options").querySelectorAll("input:checked")).map(i=>i.value));
        return core.answer(round, Object.fromEntries(Array.from(byId("qg-options").querySelectorAll("[data-answer-key]")).map(i=>[i.dataset.answerKey,i.value])));
    }
    async function submitAnswer(skipped) {
        if (pending || !round || round.answered || core.remaining(round.endsAt,offset,Date.now()) === 0) return;
        let payload;
        try { payload = lostPayload || { roundId: round.id, ...(skipped ? { skipped:true } : { answer:currentAnswer() }) }; }
        catch { error(labels.select); return; }
        pending = true; lockAnswer(true); status(labels.sending, true); text("qg-error", "");
        try { const next = await api(`${base}/answers`, payload); lostPayload = null; pending = false; apply(next); status(labels.accepted, true); }
        catch (ex) {
            // Не обещаем сохранение. Замораживаем canonical payload для безопасного retry; state подтвердит принятие.
            lostPayload = payload; pending = false; status(labels.uncertain, true); if (ex.code) error(ex.message);
            await refresh();
        } finally { pending = false; if (round) lockAnswer(round.answered || core.remaining(round.endsAt,offset,Date.now()) === 0); }
    }
    async function command(name, payload = {}) {
        if (pending) return; pending = true; text("qg-error", "");
        try { const next = await api(`${base}/${name}`, payload); pending = false; if (next) apply(next); else window.location.assign("/student/quiz"); }
        catch (ex) { pending = false; error(ex.message); await refresh(); }
        finally { pending = false; }
    }
    async function startGame() {
        if (pending || room?.status !== "waiting" || !room.currentParticipantHost) return;
        const form = byId("qg-settings");
        if (!form.hidden && !form.reportValidity()) return;
        // Считываем поля до первого запроса: polling не должен изменить выбранные правила
        // между их сохранением и запуском. Неизменённые настройки не сбрасывают готовность.
        const settings = {settingsRevision:room.settingsRevision,
            gradeId:room.gradeSelectionMode === "NONE" ? null : Number(byId("qg-grade").value),
            questionCount:Number(byId("qg-count").value),roundSeconds:Number(byId("qg-seconds").value)};
        const changed = settings.gradeId !== (room.gradeSelectionMode === "NONE" ? null : room.gradeId)
            || settings.questionCount !== room.questionCount || settings.roundSeconds !== room.roundSeconds;
        pending = true; byId("qg-start").disabled = true; form.inert = true; text("qg-error", "");
        try {
            if (changed) {
                const updated = await api(`${base}/settings`, settings);
                apply(updated);
                // В групповой игре новые правила требуют готовности остальных участников.
                // Сервер возвращает причины ожидания; запускать прежние правила нельзя.
                if (!updated.canStart) return;
            }
            apply(await api(`${base}/start`, {}));
        } catch (ex) { error(ex.message); }
        finally {
            pending = false; form.inert = false;
            byId("qg-start").disabled = !room?.canStart && !dirtySettings;
        }
    }
    function tick() {
        if (!round || room?.status !== "active") return;
        const seconds = core.remaining(round.endsAt,offset,Date.now()); text("qg-timer", seconds);
        const thresholds = core.thresholds(previousSeconds,seconds,announced);
        if (thresholds.length) text("qg-timer-live", labels.threshold.replace("{n}",thresholds.at(-1)));
        try { sessionStorage.setItem(`quiz-thresholds:${room.code}:${round.id}`,JSON.stringify([...announced])); } catch { /* Без storage дубли подавляются в текущей вкладке. */ }
        previousSeconds = seconds;
        if (seconds === 0) {
            lockAnswer(true); status(labels.timeout,false);
            if (timeoutRefreshRoundId !== round.id) { timeoutRefreshRoundId = round.id; refresh(); }
        }
    }
    async function loadResults() {
        if (resultLoaded) return; resultLoaded = true;
        try {
            const result = await api(`${base}/results`), me = result.me;
            text("qg-result-summary", `${labels.place}: ${me.place} · ${labels.score}: ${me.score}/${me.maxScore}`);
            text("qg-result-count", `${me.correctAnswers} / ${me.totalRounds}`);
            text("qg-result-percent", `${me.percent}%`);
            byId("qg-ranking").replaceChildren(...result.leaderboard.map(p => {
                const tr = node("tr"); [p.place,p.displayName,`${p.score}/${p.maxScore}`,p.totalResponseMs == null ? "—" : `${(p.totalResponseMs/1000).toFixed(3)} ${labels.secondUnit}`].forEach(v=>tr.append(node("td",v))); return tr;
            }));
            // Пропуск и истечение времени отделены от ошибок с отправленным ответом.
            const reviewState = r => r.skipped || r.timedOut ? "unanswered" : r.correct ? "correct" : "wrong";
            const filters = byId("qg-review-filters");
            filters.querySelectorAll("[data-review-count]").forEach(el => {
                const filter = el.dataset.reviewCount;
                el.textContent = result.review.filter(r => filter === "all" || reviewState(r) === filter).length;
            });
            function renderReview(filter) {
                filters.querySelectorAll("[data-review-filter]").forEach(button => button.setAttribute("aria-pressed", String(button.dataset.reviewFilter === filter)));
                const rows = result.review.filter(r => filter === "all" || reviewState(r) === filter);
                byId("qg-review").replaceChildren(...rows.map(r => {
                    const state = reviewState(r), article = node("article", null, "qg-review-row");
                    const heading = node("div", null, "qg-review-top");
                    heading.append(node("span", String(r.orderNo).padStart(2, "0"), "qg-review-number"), node("h4", r.prompt),
                        node("span", r.skipped ? labels.skippedShort : r.timedOut ? labels.timedOutShort : r.correct ? labels.correct : labels.incorrect, `qg-review-badge is-${state}`));
                    const answers = node("div", null, "qg-review-answers");
                    const own = node("span", `${labels.yourAnswer}: `);
                    own.append(node("strong", r.skipped ? labels.skippedShort : r.timedOut ? labels.timedOutShort : core.answerText(r.answer, r.options, labels), `is-${state}`));
                    answers.append(own);
                    // Верный ответ не дублируется; все форматы по-прежнему читаются из снимка игры.
                    if (state !== "correct") {
                        const expected = node("span", `${labels.correctAnswer}: `);
                        expected.append(node("strong", core.answerText(r.correctAnswer, r.options, labels), "is-correct")); answers.append(expected);
                    }
                    article.append(heading, answers);
                    if (r.explanation) {
                        const explanation = node("div", null, "qg-review-explanation");
                        explanation.append(node("span", labels.explanationTitle, "qg-review-explanation-label"), node("p", r.explanation)); article.append(explanation);
                    }
                    return article;
                }));
                if (!rows.length) byId("qg-review").append(node("p", labels.reviewEmpty, "qg-review-empty"));
            }
            renderReview("all");
            filters.onclick = event => { const button = event.target.closest("[data-review-filter]"); if (button) renderReview(button.dataset.reviewFilter); };
            status(labels.results,false); byId("qg-results-title").focus();
        } catch (ex) { resultLoaded = false; error(ex.message); setTimeout(loadResults,3000); }
    }
    // Сокет передаёт только CONNECT/SUBSCRIBE; команда игры всегда проходит CSRF-защищённый HTTP.
    function connect() {
        if (stopped) return;
        if (typeof window.WebSocket !== "function") { scheduleFallbackPoll(0); return; }
        const url = new URL("/ws/quiz",location.href); url.protocol = location.protocol === "https:" ? "wss:" : "ws:";
        socket = new WebSocket(url,["v12.stomp"]); let buffer = "";
        socket.onopen = () => socket.send("CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0");
        socket.onmessage = event => {
            buffer += String(event.data); const frames=buffer.split("\0"); buffer=frames.pop();
            for (const raw of frames) {
                const frame=raw.replace(/^\s+/,"");
                if (frame.startsWith("CONNECTED")) {
                    realtimeConnected = true; clearTimeout(pollTimer); online(true);
                    socket.send(`SUBSCRIBE\nid:quiz-game\ndestination:/topic/quiz.rooms.${root.dataset.code}\n\n\0`); refresh();
                }
                if (frame.startsWith("MESSAGE")) refresh();
                if (frame.startsWith("ERROR")) socket.close();
            }
        };
        socket.onerror = () => socket.close(); socket.onclose = () => {
            realtimeConnected = false;
            if (!stopped) { text("qg-network",labels.reconnecting); scheduleFallbackPoll(0); reconnect=setTimeout(connect,3000); }
        };
    }
    byId("qg-start").onclick = startGame; byId("qg-ready").onclick = () => command("ready",{ready:true,settingsRevision:room.settingsRevision});
    byId("qg-cancel").onclick = () => command("cancel"); byId("qg-leave").onclick = () => command("leave");
    byId("qg-invite").onclick = () => show("qg-invitation",byId("qg-invitation").hidden);
    byId("qg-copy").onclick = async () => { try { await navigator.clipboard.writeText(room.code); status(labels.copied,true); } catch { byId("qg-invite-code").select(); } };
    byId("qg-settings").addEventListener("input",()=>{dirtySettings=true; byId("qg-start").disabled=pending;});
    byId("qg-settings").onsubmit = event => { event.preventDefault(); startGame(); };
    byId("qg-answer-form").onsubmit = event => { event.preventDefault(); submitAnswer(false); }; byId("qg-skip").onclick = () => submitAnswer(true);
    const timer=setInterval(tick,250);
    window.addEventListener("online",refresh); document.addEventListener("visibilitychange",()=>{if(!document.hidden) refresh();});
    window.addEventListener("pagehide",()=>{stopped=true; clearInterval(timer); clearTimeout(reconnect); clearTimeout(pollTimer); socket?.close();});
    refresh(); connect(); scheduleFallbackPoll();
})();
