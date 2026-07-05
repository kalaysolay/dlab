(function () {
    "use strict";

    const root = document.querySelector("[data-quiz-room]");
    if (!root) {
        return;
    }

    const state = {
        room: null,
        currentRoundId: null,
        serverOffsetMs: 0,
        fallbackSeconds: null,
        fallbackRoundId: null,
        timeoutFetchRoundId: null,
        autoSubmitRoundId: null,
        submittingRoundId: null,
        socket: null,
        reconnectTimer: null,
        stopped: false
    };

    const labels = {
        host: "Хост",
        ready: "Готов",
        waiting: "Ожидает",
        currentStudent: "Это вы",
        answered: "Ответ сохранен",
        savedCanEdit: "Ответ сохранен. До конца раунда его можно изменить.",
        submitIdle: "Выберите ответ текущего раунда.",
        submitSending: "Сохраняю ответ...",
        autoSubmitSending: "Время вышло — отправляю выбранный ответ...",
        submitError: "Не удалось сохранить ответ. Обновляю состояние комнаты.",
        timedOut: "Время раунда вышло.",
        noRound: "Текущий раунд пока не открыт.",
        progress: "Ответов",
        statuses: {
            waiting: "Ожидание",
            active: "Игра идет",
            finished: "Завершена"
        }
    };

    const refs = {
        participants: document.getElementById("quizParticipants"),
        status: document.getElementById("quizStatus"),
        form: document.getElementById("quizForm"),
        timer: document.getElementById("quizTimer"),
        timerBox: document.querySelector(".compact-timer"),
        submitButton: document.getElementById("quizSubmitButton"),
        submitStatus: document.getElementById("quizSubmitStatus"),
        roundNav: document.getElementById("quizRoundNav")
    };

    function absoluteUrl(url) {
        return new URL(url, window.location.href).toString();
    }

    function websocketUrl(url) {
        const target = new URL(url, window.location.href);
        target.protocol = target.protocol === "https:" ? "wss:" : "ws:";
        return target.toString();
    }

    function csrfHeaders() {
        const token = document.querySelector('meta[name="_csrf"]')?.content
                || document.querySelector('input[name="_csrf"]')?.value;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content || "X-CSRF-TOKEN";
        return token ? { [header]: token } : {};
    }

    async function fetchRoom(reason) {
        if (!root.dataset.apiUrl) {
            return null;
        }
        const response = await fetch(absoluteUrl(root.dataset.apiUrl), {
            credentials: "same-origin",
            headers: { "Accept": "application/json" }
        });
        if (!response.ok) {
            return null;
        }
        const room = await response.json();
        applyRoom(room, reason);
        return room;
    }

    function applyRoom(room, reason) {
        state.room = room;
        root.dataset.roomStatus = room.status || root.dataset.roomStatus || "";
        if (room.serverTime) {
            const serverNow = Date.parse(room.serverTime);
            if (Number.isFinite(serverNow)) {
                state.serverOffsetMs = serverNow - Date.now();
            }
        }

        updateStatus(room.status);
        renderParticipants(room);

        if (room.status === "finished") {
            goToResults();
            return;
        }

        if (room.status === "active" && !refs.form) {
            window.location.reload();
            return;
        }

        if (refs.form && room.status === "active") {
            syncRounds(room);
            showCurrentRound(selectCurrentRound(room));
        }

        if (reason === "room.started" && !refs.form) {
            window.location.reload();
        }
    }

    function updateStatus(status) {
        if (refs.status && status) {
            refs.status.textContent = labels.statuses[status] || status;
        }
    }

    function renderParticipants(room) {
        if (!refs.participants) {
            return;
        }
        const totalRounds = (room.rounds || []).length || Number(refs.participants.dataset.roundCount || 0);
        refs.participants.dataset.roundCount = String(totalRounds);
        refs.participants.replaceChildren(...(room.participants || []).map((participant) => {
            const row = document.createElement("div");
            row.className = "participant-row";

            const main = document.createElement("div");
            main.className = "participant-main";
            const name = document.createElement("strong");
            name.textContent = participant.displayName || "";
            main.appendChild(name);
            if (participant.currentStudent) {
                const current = document.createElement("span");
                current.className = "muted-line";
                current.textContent = labels.currentStudent;
                main.appendChild(current);
            }
            row.appendChild(main);

            if (participant.host) {
                row.appendChild(badge(labels.host, "warning"));
            }
            row.appendChild(badge(participant.ready ? labels.ready : labels.waiting, participant.ready ? "success" : ""));

            if (room.status === "active" || room.status === "finished") {
                const progress = document.createElement("span");
                progress.className = "muted-line quiz-answer-progress";
                progress.textContent = `${labels.progress}: ${participant.answeredRounds || 0}/${totalRounds}`;
                row.appendChild(progress);
            }
            return row;
        }));
    }

    function badge(text, tone) {
        const element = document.createElement("span");
        element.className = tone ? `badge ${tone}` : "badge";
        element.textContent = text;
        return element;
    }

    function syncRounds(room) {
        const roundsById = new Map((room.rounds || []).map((round) => [String(round.id), round]));
        document.querySelectorAll(".quiz-round-panel").forEach((panel) => {
            const round = roundsById.get(panel.dataset.roundId);
            if (!round) {
                return;
            }
            panel.dataset.roundAnswered = String(Boolean(round.answered));
            panel.dataset.roundTimedOut = String(Boolean(round.timedOut));
            if (round.startsAt) {
                panel.dataset.roundStartsAt = round.startsAt;
            }
            if (round.endsAt) {
                panel.dataset.roundEndsAt = round.endsAt;
            }
            syncAnsweredBadge(panel, Boolean(round.answered));
        });
    }

    function syncAnsweredBadge(panel, answered) {
        const kicker = panel.querySelector(".question-kicker");
        if (!kicker) {
            return;
        }
        let marker = kicker.querySelector(".quiz-live-answer-badge");
        if (answered && !marker) {
            marker = badge(labels.answered, "success");
            marker.classList.add("quiz-live-answer-badge");
            kicker.appendChild(marker);
        } else if (!answered && marker) {
            marker.remove();
        }
    }

    function selectCurrentRound(room) {
        const rounds = room.rounds || [];
        const now = serverNow();
        const byTime = rounds.find((round) => {
            const startsAt = Date.parse(round.startsAt || "");
            const endsAt = Date.parse(round.endsAt || "");
            return Number.isFinite(startsAt) && Number.isFinite(endsAt) && startsAt <= now && now < endsAt;
        });
        if (byTime) {
            return byTime;
        }
        if (room.activeRoundId != null) {
            const active = rounds.find((round) => String(round.id) === String(room.activeRoundId));
            if (active) {
                return active;
            }
        }
        return rounds.find((round) => !round.answered && !round.timedOut) || rounds[0] || null;
    }

    function showCurrentRound(round) {
        root.classList.add("quiz-live-ready");
        state.currentRoundId = round ? String(round.id) : null;

        document.querySelectorAll(".quiz-round-panel").forEach((panel) => {
            const active = round && panel.dataset.roundId === String(round.id);
            panel.hidden = !active;
            panel.classList.toggle("active", Boolean(active));
        });

        document.querySelectorAll("[data-round-link]").forEach((link) => {
            const linkRound = findRound(link.dataset.roundLink);
            link.classList.toggle("active", round && link.dataset.roundLink === String(round.id));
            link.classList.toggle("answered", Boolean(linkRound?.answered));
            link.setAttribute("aria-current", round && link.dataset.roundLink === String(round.id) ? "step" : "false");
        });

        resetFallbackTimer(round);
        updateSubmitState();
        renderTimer();
    }

    function findRound(roundId) {
        return (state.room?.rounds || []).find((round) => String(round.id) === String(roundId));
    }

    function currentRound() {
        return state.currentRoundId ? findRound(state.currentRoundId) : null;
    }

    function currentPanel() {
        if (!state.currentRoundId) {
            return null;
        }
        return Array.from(document.querySelectorAll(".quiz-round-panel"))
                .find((panel) => panel.dataset.roundId === state.currentRoundId) || null;
    }

    function serverNow() {
        return Date.now() + state.serverOffsetMs;
    }

    function secondsRemaining(round) {
        if (round?.endsAt) {
            const endsAt = Date.parse(round.endsAt);
            if (Number.isFinite(endsAt)) {
                return Math.max(0, Math.ceil((endsAt - serverNow()) / 1000));
            }
        }
        if (state.fallbackSeconds != null) {
            return Math.max(0, state.fallbackSeconds);
        }
        return Number(refs.timerBox?.dataset.seconds || 0);
    }

    function resetFallbackTimer(round) {
        if (!round || round.endsAt || state.fallbackRoundId === String(round.id)) {
            return;
        }
        state.fallbackRoundId = String(round.id);
        state.fallbackSeconds = Number(refs.timerBox?.dataset.seconds || 0);
    }

    function renderTimer() {
        if (!refs.timer) {
            return;
        }
        const round = currentRound();
        const seconds = secondsRemaining(round);
        const minutes = String(Math.floor(seconds / 60)).padStart(2, "0");
        const rest = String(seconds % 60).padStart(2, "0");
        refs.timer.textContent = `${minutes}:${rest}`;
        updateSubmitState();
        if (round && seconds === 1 && !round.answered && state.autoSubmitRoundId !== String(round.id)) {
            const panel = currentPanel();
            if (hasDraftAnswer(panel)) {
                state.autoSubmitRoundId = String(round.id);
                handleRoundTimeout(round).catch(() => undefined);
            }
        }
        if (round && seconds === 0 && !round.timedOut && state.timeoutFetchRoundId !== String(round.id)) {
            state.timeoutFetchRoundId = String(round.id);
            handleRoundTimeout(round).catch(() => fetchRoom("timer.timeout").catch(() => undefined));
        }
    }

    function updateSubmitState() {
        if (!refs.submitButton) {
            return;
        }
        const round = currentRound();
        const timedOut = !round || round.timedOut || secondsRemaining(round) <= 0;
        refs.submitButton.disabled = timedOut;
        setStatus(timedOut ? (round ? labels.timedOut : labels.noRound) : (round?.answered ? labels.savedCanEdit : labels.submitIdle), !round);
    }

    function setStatus(text, hidden) {
        if (!refs.submitStatus) {
            return;
        }
        refs.submitStatus.textContent = text || "";
        refs.submitStatus.hidden = Boolean(hidden || !text);
    }

    function hasDraftAnswer(panel) {
        if (!panel) {
            return false;
        }
        const type = panel.dataset.roundType;
        if (type === "MATCHING") {
            return Array.from(panel.querySelectorAll(".matching-answer select"))
                    .some((select) => Boolean(select.value));
        }
        if (type === "FILL_IN") {
            return Array.from(panel.querySelectorAll("input[data-fill-placeholder]"))
                    .some((input) => Boolean(input.value.trim()));
        }
        return panel.querySelector('input[type="radio"]:checked, input[type="checkbox"]:checked') !== null;
    }

    async function submitRound(round, panel, reason) {
        if (!panel || !round || round.answered || state.submittingRoundId === String(round.id)) {
            return false;
        }
        const autoSubmit = reason === "timer.auto-submit";
        state.submittingRoundId = String(round.id);
        refs.submitButton.disabled = true;
        setStatus(autoSubmit ? labels.autoSubmitSending : labels.submitSending, false);
        try {
            const response = await fetch(absoluteUrl(root.dataset.apiUrl + "/answers"), {
                method: "POST",
                credentials: "same-origin",
                headers: {
                    "Accept": "application/json",
                    "Content-Type": "application/json",
                    ...csrfHeaders()
                },
                body: JSON.stringify({
                    roundId: Number(round.id),
                    answer: answerPayload(panel)
                })
            });
            if (!response.ok) {
                throw new Error(`answer failed: ${response.status}`);
            }
            applyRoom(await response.json(), reason || "answer.submit");
            return true;
        } catch (error) {
            setStatus(labels.submitError, false);
            await fetchRoom("answer.error");
            return false;
        } finally {
            if (state.submittingRoundId === String(round.id)) {
                state.submittingRoundId = null;
            }
            updateSubmitState();
        }
    }

    async function handleRoundTimeout(round) {
        if (!round || round.answered) {
            await fetchRoom("timer.timeout");
            return;
        }
        if (state.submittingRoundId === String(round.id)) {
            return;
        }
        const panel = currentPanel();
        if (hasDraftAnswer(panel)) {
            const submitted = await submitRound(round, panel, "timer.auto-submit");
            if (!submitted) {
                await fetchRoom("timer.timeout");
            }
            return;
        }
        await fetchRoom("timer.timeout");
    }

    async function submitCurrentRound(event) {
        event.preventDefault();
        await submitRound(currentRound(), currentPanel(), "answer.submit");
    }

    function answerPayload(panel) {
        const type = panel.dataset.roundType;
        if (type === "MATCHING") {
            const pairs = {};
            panel.querySelectorAll(".matching-answer .field").forEach((field) => {
                const left = field.dataset.leftValue || field.querySelector("span")?.textContent || "";
                const right = field.querySelector("select")?.value || "";
                if (left && right) {
                    pairs[left] = right;
                }
            });
            return { pairs };
        }
        if (type === "FILL_IN") {
            const answers = {};
            panel.querySelectorAll("input[data-fill-placeholder]").forEach((input) => {
                const placeholder = input.dataset.fillPlaceholder;
                const value = input.value || "";
                if (placeholder && value.trim()) {
                    answers[placeholder] = value;
                }
            });
            return { answers };
        }
        const selected = Array.from(panel.querySelectorAll('input[type="radio"]:checked, input[type="checkbox"]:checked'))
                .map((input) => input.value);
        return { selected };
    }

    function goToResults() {
        const url = root.dataset.resultsUrl;
        if (url && !window.location.pathname.endsWith("/results")) {
            window.location.assign(absoluteUrl(url));
        }
    }

    function parseFrame(raw) {
        const separator = raw.indexOf("\n\n");
        const head = separator >= 0 ? raw.slice(0, separator) : raw;
        const body = separator >= 0 ? raw.slice(separator + 2) : "";
        const lines = head.split("\n").filter(Boolean);
        const command = lines.shift() || "";
        const headers = {};
        lines.forEach((line) => {
            const split = line.indexOf(":");
            if (split > -1) {
                headers[line.slice(0, split)] = line.slice(split + 1);
            }
        });
        return { command, headers, body };
    }

    function stompFrame(command, headers, body) {
        const headerLines = Object.entries(headers || {}).map(([key, value]) => `${key}:${value}`);
        return `${command}\n${headerLines.join("\n")}\n\n${body || ""}\0`;
    }

    function connectStomp() {
        if (!root.dataset.wsUrl || !root.dataset.roomCode || !("WebSocket" in window)) {
            return;
        }
        try {
            const socket = new WebSocket(websocketUrl(root.dataset.wsUrl), ["v10.stomp", "v11.stomp", "v12.stomp"]);
            state.socket = socket;

            socket.addEventListener("open", () => {
                socket.send(stompFrame("CONNECT", {
                    "accept-version": "1.2",
                    "heart-beat": "10000,10000"
                }));
            });

            socket.addEventListener("message", (event) => {
                String(event.data).split("\0").forEach((chunk) => {
                    const raw = chunk.trim();
                    if (!raw) {
                        return;
                    }
                    const frame = parseFrame(raw);
                    if (frame.command === "CONNECTED") {
                        socket.send(stompFrame("SUBSCRIBE", {
                            id: "quiz-room",
                            destination: `/topic/quiz.rooms.${root.dataset.roomCode}`
                        }));
                    }
                    if (frame.command === "MESSAGE") {
                        handleStompMessage(frame.body);
                    }
                });
            });

            socket.addEventListener("close", scheduleReconnect);
            socket.addEventListener("error", () => socket.close());
        } catch (error) {
            scheduleReconnect();
        }
    }

    function scheduleReconnect() {
        if (state.stopped || state.reconnectTimer) {
            return;
        }
        state.reconnectTimer = window.setTimeout(() => {
            state.reconnectTimer = null;
            connectStomp();
        }, 3000);
    }

    function handleStompMessage(body) {
        let event = null;
        try {
            event = JSON.parse(body);
        } catch (error) {
            return;
        }
        if (event.code && event.code !== root.dataset.roomCode) {
            return;
        }
        fetchRoom(event.type || "stomp");
    }

    function tickFallbackTimer() {
        if (state.fallbackSeconds != null && state.fallbackSeconds > 0) {
            state.fallbackSeconds -= 1;
        }
        renderTimer();
    }

    refs.form?.addEventListener("submit", submitCurrentRound);
    refs.roundNav?.addEventListener("click", (event) => {
        const link = event.target.closest("[data-round-link]");
        if (link && link.dataset.roundLink !== state.currentRoundId) {
            event.preventDefault();
        }
    });
    window.addEventListener("beforeunload", () => {
        state.stopped = true;
        state.socket?.close();
    });

    /*
     * Заменяет метки [[N]] в тексте FILL_IN вопросов на поля ввода.
     * Вызывается один раз при загрузке — все раунды уже в DOM (скрытые в том числе).
     * Логика идентична renderInlineFillQuestions() из test-session.html.
     */
    function renderInlineFillQuestions() {
        const placeholderPattern = /(\[\[\d+]])/g;
        document.querySelectorAll(".fill-inline-question").forEach((block) => {
            const body = block.querySelector(".fill-inline-body");
            const bank = block.querySelector(".fill-inline-bank");
            if (!body || !bank) return;
            const source = block.dataset.fillBody || body.textContent || "";
            const inputByPlaceholder = new Map();
            bank.querySelectorAll("input[data-fill-placeholder]").forEach((input) => {
                input.classList.add("fill-inline-input");
                inputByPlaceholder.set(input.dataset.fillPlaceholder, input);
            });
            const parts = source.split(placeholderPattern);
            body.textContent = "";
            parts.forEach((part) => {
                const input = inputByPlaceholder.get(part);
                if (input) {
                    body.appendChild(input);
                } else if (part) {
                    body.appendChild(document.createTextNode(part));
                }
            });
        });
    }

    renderInlineFillQuestions();

    fetchRoom("initial").catch(() => {
        if (refs.form) {
            const firstPanel = document.querySelector(".quiz-round-panel");
            showCurrentRound(firstPanel ? { id: firstPanel.dataset.roundId } : null);
        }
    });
    connectStomp();
    window.setInterval(tickFallbackTimer, 1000);
    window.setInterval(() => fetchRoom("poll").catch(() => undefined), 3000);
})();
