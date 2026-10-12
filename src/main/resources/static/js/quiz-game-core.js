/* Чистые функции протокола нового UI; ключи ответов сюда не передаются. Общий модуль проверяется клиентскими тестами. */
(function (global) {
    "use strict";
    const core = {
        normalizeCode(value) {
            const code = String(value || "").trim().toUpperCase();
            if (!/^QZ[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}$/.test(code)) throw new Error("ROOM_CODE_INVALID");
            return code;
        },
        // false проверяется по типу, поэтому не превращается в отсутствие ответа.
        answer(round, values) {
            if (round.contentKind === "FACT") {
                if (typeof values !== "boolean") throw new Error("ANSWER_INVALID");
                return values;
            }
            if (["SCQ", "MCQ"].includes(round.format) || round.contentKind === "VOCABULARY") {
                if (!Array.isArray(values) || !values.length || (round.format !== "MCQ" && values.length !== 1)) throw new Error("ANSWER_INVALID");
                return { selected: values };
            }
            if (round.format === "MATCHING") return { pairs: values };
            if (round.format === "FILL_IN") return { answers: values };
            throw new Error("ANSWER_INVALID");
        },
        remaining(end, offset, now) { return Math.max(0, Math.ceil((Date.parse(end) - (now + offset)) / 1000)); },
        // Set хранится между poll/reconnect и в sessionStorage при reload текущего окна.
        thresholds(previous, current, announced) {
            return [10, 5].filter(n => previous > n && current <= n && !announced.has(n)).map(n => { announced.add(n); return n; });
        },
        // Личные ответы и ключи становятся текстом только в FINISHED-разборе, без innerHTML.
        answerText(answer, options, labels) {
            if (answer == null) return labels.noAnswer;
            if (typeof answer === "boolean") return answer ? labels.true : labels.false;
            const choices = Array.isArray(options) ? options : [];
            const choice = id => choices.find(o => o.id === id)?.text || String(id);
            if (typeof answer === "string") return choice(answer);
            if (Array.isArray(answer)) return answer.map(choice).join(", ");
            if (answer.selected) return answerTextSelected(answer.selected);
            if (answer.pairs) {
                if (Array.isArray(answer.pairs)) return answer.pairs.map(p => `${matching("left",p.left)} → ${matching("right",p.right)}`).join("; ");
                return Object.entries(answer.pairs).map(([l,r]) => `${matching("left",l)} → ${matching("right",r)}`).join("; ");
            }
            return Object.entries(answer.answers || answer).map(([k,v]) => `${k}: ${v}`).join("; ");
            function answerTextSelected(selected) { return (Array.isArray(selected) ? selected : [selected]).map(choice).join(", "); }
            function matching(side,id) { return options?.[side]?.find(o => o.id.toLowerCase() === String(id).toLowerCase())?.text || id; }
        }
    };
    if (typeof module !== "undefined") module.exports = core;
    global.QuizGameCore = core;
})(typeof window === "undefined" ? globalThis : window);
