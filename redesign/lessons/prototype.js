const questions = [
    {
        type: "MCQ",
        topic: "Разряды десятичной дроби",
        text: "Какая цифра в числе 7,362 находится в разряде десятых?",
        answers: ["7", "3", "6", "2"]
    },
    {
        type: "MCQ",
        topic: "Правило округления",
        text: "Что нужно сделать, если справа от выбранного разряда стоит цифра 4?",
        answers: ["Увеличить цифру на один", "Оставить цифру без изменений", "Заменить число нулём", "Перенести запятую"]
    },
    {
        type: "FILL IN",
        topic: "Правило округления",
        text: "Округлите число 7,362 до десятых и впишите результат.",
        placeholder: "Например, 7,5"
    },
    {
        type: "MCQ",
        topic: "Округление до сотых",
        text: "Округлите число 12,486 до сотых.",
        answers: ["12,48", "12,49", "12,50", "12,40"]
    },
    {
        type: "MATCH",
        topic: "Соответствия",
        text: "Сопоставьте условие округления с правильным действием.",
        pairs: ["Следующая цифра 0–4", "Следующая цифра 5–9", "7,362 до десятых"],
        options: ["Оставляем цифру без изменений", "Увеличиваем цифру на один", "Получаем 7,4"]
    },
    {
        type: "MCQ",
        topic: "Проверка понимания",
        text: "Какое утверждение об округлении верно?",
        answers: ["Всегда смотрим на цифру слева", "Всегда увеличиваем выбранную цифру", "Результат должен быть удобнее и близок к исходному", "Запятую всегда убирают"]
    }
];

const state = {
    currentQuestion: 2,
    answers: [1, 1, null, null, null, null]
};

const tabs = Array.from(document.querySelectorAll("[data-tab]"));
const panels = Array.from(document.querySelectorAll("[data-panel]"));
const questionText = document.getElementById("questionText");
const questionTopic = document.getElementById("questionTopic");
const questionType = document.getElementById("questionType");
const questionLabel = document.getElementById("questionLabel");
const answerList = document.getElementById("answerList");
const questionNumbers = document.getElementById("questionNumbers");
const answeredCount = document.getElementById("answeredCount");
const tabAnswered = document.getElementById("tabAnswered");
const testingTab = document.getElementById("testingTab");
const theoryActions = document.getElementById("theoryActions");
const noTestCompletion = document.getElementById("noTestCompletion");
const lessonScenario = document.getElementById("lessonScenario");
const previousQuestion = document.getElementById("previousQuestion");
const nextQuestion = document.getElementById("nextQuestion");
const stickyToggle = document.getElementById("stickyToggle");
const mediaModal = document.getElementById("mediaModal");
const mediaStage = document.getElementById("mediaStage");
const mediaTitle = document.getElementById("mediaTitle");
const mediaKind = document.getElementById("mediaKind");
const finishModal = document.getElementById("finishModal");
const finishCopy = document.getElementById("finishCopy");
const finishTitle = document.getElementById("finishTitle");
const finishIcon = document.getElementById("finishIcon");
const finishPrimary = document.getElementById("finishPrimary");
const missingQuestions = document.getElementById("missingQuestions");
const toast = document.getElementById("toast");

function openTab(name, shouldScroll = true) {
    tabs.forEach((tab) => tab.classList.toggle("active", tab.dataset.tab === name));
    panels.forEach((panel) => {
        const active = panel.dataset.panel === name;
        panel.classList.toggle("active", active);
        panel.hidden = !active;
    });
    if (name === "testing") {
        renderQuestion();
    }
    if (shouldScroll) {
        document.querySelector(".lesson-tabs").scrollIntoView({ behavior: "smooth", block: "start" });
    }
}

function isQuestionAnswered(answer, question) {
    if (question.type === "FILL IN") return typeof answer === "string" && answer.trim().length > 0;
    if (question.type === "MATCH") return Array.isArray(answer) && answer.length === question.pairs.length && answer.every(Boolean);
    return Number.isInteger(answer);
}

function answeredTotal() {
    return state.answers.filter((answer, index) => isQuestionAnswered(answer, questions[index])).length;
}

function renderMcq(question) {
    question.answers.forEach((answer, index) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "answer-option";
        button.classList.toggle("selected", state.answers[state.currentQuestion] === index);
        button.setAttribute("aria-pressed", state.answers[state.currentQuestion] === index ? "true" : "false");
        button.innerHTML = `<span class="answer-radio"></span><span class="answer-letter">${String.fromCharCode(65 + index)}</span><span>${answer}</span>`;
        button.addEventListener("click", () => {
            state.answers[state.currentQuestion] = index;
            renderQuestion();
        });
        answerList.appendChild(button);
    });
}

function renderFillIn(question) {
    const wrapper = document.createElement("label");
    wrapper.className = "fill-answer";
    const label = document.createElement("span");
    label.textContent = "Ваш ответ";
    const input = document.createElement("input");
    input.type = "text";
    input.inputMode = "decimal";
    input.placeholder = question.placeholder;
    input.value = typeof state.answers[state.currentQuestion] === "string" ? state.answers[state.currentQuestion] : "";
    input.addEventListener("input", () => {
        state.answers[state.currentQuestion] = input.value;
        renderQuestionNavigation();
    });
    wrapper.append(label, input);
    answerList.appendChild(wrapper);
}

function renderMatch(question) {
    const values = Array.isArray(state.answers[state.currentQuestion])
        ? state.answers[state.currentQuestion]
        : Array(question.pairs.length).fill("");
    state.answers[state.currentQuestion] = values;
    const list = document.createElement("div");
    list.className = "match-list";
    question.pairs.forEach((pair, index) => {
        const row = document.createElement("label");
        row.className = "match-row";
        const left = document.createElement("span");
        left.textContent = pair;
        const select = document.createElement("select");
        select.setAttribute("aria-label", `Соответствие для: ${pair}`);
        const placeholder = document.createElement("option");
        placeholder.value = "";
        placeholder.textContent = "Выберите соответствие";
        select.appendChild(placeholder);
        question.options.forEach((option) => {
            const item = document.createElement("option");
            item.value = option;
            item.textContent = option;
            select.appendChild(item);
        });
        select.value = values[index] || "";
        select.addEventListener("change", () => {
            values[index] = select.value;
            renderQuestionNavigation();
        });
        row.append(left, select);
        list.appendChild(row);
    });
    answerList.appendChild(list);
}

function renderQuestionNavigation() {
    questionNumbers.innerHTML = "";

    questions.forEach((_, index) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "question-number";
        button.textContent = String(index + 1);
        button.classList.toggle("answered", isQuestionAnswered(state.answers[index], questions[index]) && index !== state.currentQuestion);
        button.classList.toggle("current", index === state.currentQuestion);
        button.setAttribute("aria-label", `Перейти к вопросу ${index + 1}`);
        button.addEventListener("click", () => {
            state.currentQuestion = index;
            renderQuestion();
        });
        questionNumbers.appendChild(button);
    });

    const total = answeredTotal();
    answeredCount.textContent = String(total);
    tabAnswered.textContent = `${total}/${questions.length}`;
    previousQuestion.disabled = state.currentQuestion === 0;
    if (state.currentQuestion === questions.length - 1) {
        nextQuestion.textContent = answeredTotal() === questions.length ? "Завершить тест" : "Проверить ответы";
    } else {
        nextQuestion.textContent = "Дальше";
    }
}

function renderQuestion() {
    const question = questions[state.currentQuestion];
    questionLabel.textContent = `Вопрос ${state.currentQuestion + 1} из ${questions.length}`;
    questionTopic.textContent = question.topic;
    questionType.textContent = question.type;
    questionText.textContent = question.text;
    answerList.innerHTML = "";

    if (question.type === "FILL IN") renderFillIn(question);
    else if (question.type === "MATCH") renderMatch(question);
    else renderMcq(question);

    renderQuestionNavigation();
}

function openFinishState() {
    const unanswered = state.answers.map((answer, index) => isQuestionAnswered(answer, questions[index]) ? -1 : index).filter((index) => index >= 0);
    const isComplete = unanswered.length === 0;
    finishTitle.textContent = isComplete ? "Тест завершён" : "Остались вопросы";
    finishIcon.setAttribute("href", isComplete ? "../../../src/main/resources/static/icons/lucide-sprite.svg#shield-check" : "../../../src/main/resources/static/icons/lucide-sprite.svg#route");
    finishCopy.textContent = isComplete
        ? "Вы ответили на все вопросы урока. Результат можно сохранить и перейти дальше."
        : `Отвечено ${answeredTotal()} из ${questions.length}. Ответьте на оставшиеся вопросы, чтобы завершить тест.`;
    missingQuestions.innerHTML = "";
    missingQuestions.hidden = isComplete;
    if (!isComplete) {
        const label = document.createElement("span");
        label.textContent = "Пропущенные вопросы:";
        missingQuestions.appendChild(label);
        unanswered.forEach((index) => {
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = String(index + 1);
            button.setAttribute("aria-label", `Открыть вопрос ${index + 1}`);
            button.addEventListener("click", () => {
                state.currentQuestion = index;
                closeLayer(finishModal);
                renderQuestion();
                document.querySelector(".question-card").scrollIntoView({ behavior: "smooth", block: "center" });
            });
            missingQuestions.appendChild(button);
        });
    }
    finishPrimary.textContent = isComplete ? "Продолжить обучение" : "Вернуться к вопросам";
    finishPrimary.onclick = () => {
        closeLayer(finishModal);
        if (!isComplete) document.querySelector(".question-card").scrollIntoView({ behavior: "smooth", block: "center" });
    };
    finishModal.hidden = false;
    document.body.classList.add("modal-open");
}

function moveQuestion(direction) {
    const next = state.currentQuestion + direction;
    if (next >= 0 && next < questions.length) {
        state.currentQuestion = next;
        renderQuestion();
        document.querySelector(".question-card").scrollIntoView({ behavior: "smooth", block: "center" });
        return;
    }
    if (direction > 0 && state.currentQuestion === questions.length - 1) {
        openFinishState();
    }
}

function showToast(message) {
    toast.textContent = message;
    toast.classList.add("show");
    window.setTimeout(() => toast.classList.remove("show"), 1800);
}

function openMedia(trigger) {
    const container = trigger.closest("[data-media]") || trigger;
    const kind = container.dataset.media;
    mediaStage.innerHTML = "";
    mediaKind.textContent = kind === "video" ? "Видео" : kind === "gif" ? "GIF-анимация" : "Изображение";
    mediaTitle.textContent = container.dataset.title || (kind === "video" ? "Округление на числовой прямой" : "Материал урока");

    if (kind === "video") {
        const video = document.createElement("div");
        video.className = "mock-video";
        video.innerHTML = `<div><svg aria-hidden="true"><use href="../../../src/main/resources/static/icons/lucide-sprite.svg#brain"></use></svg><strong>Видео встроено в урок</strong><span>В рабочей версии здесь запускается плеер без перехода на другую страницу.</span></div>`;
        mediaStage.appendChild(video);
    } else {
        const image = document.createElement("img");
        image.src = container.dataset.src || container.querySelector("img")?.src || "";
        image.alt = container.dataset.title || "Материал урока";
        mediaStage.appendChild(image);
    }
    mediaModal.hidden = false;
    document.body.classList.add("modal-open");
}

function closeLayer(layer) {
    layer.hidden = true;
    document.body.classList.remove("modal-open");
}

tabs.forEach((tab) => tab.addEventListener("click", () => openTab(tab.dataset.tab)));
document.querySelectorAll("[data-open-testing]").forEach((button) => button.addEventListener("click", () => openTab("testing")));
previousQuestion.addEventListener("click", () => moveQuestion(-1));
nextQuestion.addEventListener("click", () => moveQuestion(1));

stickyToggle.addEventListener("change", () => {
    document.body.classList.toggle("tabs-sticky", stickyToggle.checked);
    stickyToggle.closest("label").querySelector("b").textContent = stickyToggle.checked ? "Вкладки закреплены" : "Вкладки в потоке";
    showToast(stickyToggle.checked ? "Вкладки закреплены под шапкой" : "Вкладки прокручиваются вместе с уроком");
});

function setScenario(scenario, notify = true) {
    const hasTest = scenario === "with-test";
    testingTab.hidden = !hasTest;
    theoryActions.hidden = !hasTest;
    noTestCompletion.hidden = hasTest;
    if (!hasTest && document.querySelector(".lesson-tab.active")?.dataset.tab === "testing") {
        openTab("theory", false);
    }
    if (notify) showToast(hasTest ? "Показан урок с тестированием" : "Показан урок без тестирования");
}

lessonScenario.addEventListener("change", () => setScenario(lessonScenario.value));
document.getElementById("completeLesson").addEventListener("click", () => showToast("Урок завершён — возвращаемся к списку уроков"));

document.querySelectorAll(".media-open, [data-media='video']").forEach((button) => button.addEventListener("click", () => openMedia(button)));
document.querySelectorAll("[data-close-media]").forEach((button) => button.addEventListener("click", () => closeLayer(mediaModal)));
document.querySelectorAll("[data-close-finish]").forEach((button) => button.addEventListener("click", () => closeLayer(finishModal)));
document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
        closeLayer(mediaModal);
        closeLayer(finishModal);
    }
});

const outlineLinks = Array.from(document.querySelectorAll(".lesson-outline a"));
const outlineSections = outlineLinks.map((link) => document.querySelector(link.getAttribute("href"))).filter(Boolean);
if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver((entries) => {
        const visible = entries.filter((entry) => entry.isIntersecting).sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (!visible) return;
        outlineLinks.forEach((link) => link.classList.toggle("active", link.getAttribute("href") === `#${visible.target.id}`));
    }, { rootMargin: "-28% 0px -60%", threshold: [0.05, 0.25, 0.5] });
    outlineSections.forEach((section) => observer.observe(section));
}

renderQuestion();
openTab("theory", false);
setScenario(lessonScenario.value, false);
