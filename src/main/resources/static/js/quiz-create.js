/* Классы и число вопросов приходят с сервера; сервер повторно проверяет актуальный банк при создании. */
(function () {
    "use strict";
    const form = document.querySelector("[data-student-quiz-create]");
    if (!form) return;
    const subject = form.elements.subjectId, grade = form.elements.gradeId;
    const count = form.elements.questionCount, message = document.getElementById("quiz-available");
    const placeholder = grade.options[0].cloneNode(true);
    const options = Array.from(grade.options).slice(1).map(option => option.cloneNode(true));
    function availability() {
        const selected = grade.selectedOptions[0];
        const available = Number(selected?.dataset.count || 0);
        message.textContent = selected?.dataset.count ? message.dataset.label.replace("{0}", available) : "";
        count.setCustomValidity(available && Number(count.value) > available ? message.textContent : "");
    }
    function updateGrades() {
        const previous = grade.value;
        const matching = options.filter(option => option.dataset.subject === subject.value);
        grade.replaceChildren(placeholder.cloneNode(true), ...matching.map(option => option.cloneNode(true)));
        grade.value = matching.some(option => option.value === previous) ? previous : "";
        grade.disabled = !subject.value;
        availability();
    }
    subject.addEventListener("change", updateGrades);
    grade.addEventListener("change", availability);
    count.addEventListener("input", availability);
    updateGrades();
})();
