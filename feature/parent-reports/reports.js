"use strict";

// Общие демонстрационные данные для страницы и сообщений: цифры в них совпадают.
// Время показано отдельно от результатов тестов и не выдаётся за существующее поле API.
const children = {
  amir: { name: "Амир", reportName: "Амира", grade: 5, tip: "Разобрать условие вместе", advice: "В вычислениях Амир увереннее. Пока сложнее выделять главное в текстовой задаче. Предложите сначала пересказать условие своими словами, а уже потом считать.", phrase: "«Покажи, как ты понял задачу. Мне интересно, как ты рассуждаешь»." },
  aiya: { name: "Айя", reportName: "Айи", grade: 6, tip: "Собрать историю по порядку", advice: "Айя хорошо запоминает факты, но иногда путает последовательность событий. Вместе нарисуйте короткую линию времени из трёх событий и обсудите, что было раньше.", phrase: "«Какое событие было первым? Давай соберём эту историю вместе»." }
};
const weeks = {
  current: { label: "19–25 октября", full: "19–25 октября 2026", dates: [19, 20, 21, 22, 23, 24, 25] },
  previous: { label: "12–18 октября", full: "12–18 октября 2026", dates: [12, 13, 14, 15, 16, 17, 18] },
  empty: { label: "5–11 октября", full: "5–11 октября 2026", dates: [5, 6, 7, 8, 9, 10, 11] },
  first: { label: "28 сентября – 4 октября", full: "28 сентября – 4 октября 2026", dates: [28, 29, 30, 1, 2, 3, 4] }
};
const test = (day, subject, title, correct, total, detail) => ({ type: "test", day, subject, title, correct, total, detail });
const lesson = (day, subject, title, detail) => ({ type: "lesson", day, subject, title, detail });
const currentReports = {
  amir: {
    minutes: [20, 25, 0, 30, 30, 25, 0],
    activities: [
      test(0, "Математика", "Сравнение дробей", 8, 10, "Получается сравнивать дроби с одинаковым знаменателем. Стоит повторить приведение к общему знаменателю."),
      lesson(0, "Математика", "Как сравнивать дроби", "Прочитана лекция и завершена проверка понимания."),
      test(1, "Қазақ тілі", "Жалғаулар", 6, 8, "Повторить выбор падежного окончания в предложении."),
      lesson(1, "Қазақ тілі", "Септік жалғаулары", "Прочитана лекция и разобраны примеры окончаний."),
      test(3, "Математика", "Текстовые задачи на дроби", 6, 8, "Две ошибки в выборе действия. Полезно сначала выделить, что известно и что нужно найти."),
      test(3, "История Казахстана", "Древние кочевники", 5, 6, "Верно определены основные особенности быта. Стоит повторить хронологию."),
      lesson(3, "История Казахстана", "Жизнь древних кочевников", "Завершена лекция о быте и занятиях кочевников."),
      test(4, "Қазақ тілі", "Окончания в предложениях", 7, 8, "Падежные окончания получаются лучше: 7 верных ответов из 8."),
      lesson(4, "Математика", "Разбираем условие задачи", "Прочитана лекция с примерами разбора текстовых задач."),
      test(5, "История Казахстана", "События и даты", 6, 8, "Верно сопоставлены 6 событий. Две даты стоит повторить.")
    ]
  },
  aiya: {
    minutes: [25, 0, 20, 0, 30, 25, 0],
    activities: [
      test(0, "Математика", "Отношения и пропорции", 7, 8, "Уверенно составляет пропорции. Одна ошибка при вычислении неизвестного члена."),
      lesson(0, "Математика", "Основное свойство пропорции", "Завершена лекция и разобраны примеры."),
      test(2, "Қазақ тілі", "Етістік", 6, 8, "Полезно повторить формы прошедшего времени."),
      lesson(2, "Қазақ тілі", "Етістіктің шақтары", "Прочитана лекция о временах глагола."),
      test(4, "История Казахстана", "Средневековые города", 8, 10, "Хорошо определяет города. Две ошибки связаны с последовательностью событий."),
      lesson(4, "История Казахстана", "Города Великого шёлкового пути", "Завершена лекция о торговле и средневековых городах."),
      test(5, "История Казахстана", "Историческая хронология", 5, 6, "Верно расположены 5 событий. Стоит ещё раз повторить порядок событий.")
    ]
  }
};
const previousReports = {
  amir: {
    minutes: [30, 25, 25, 0, 35, 35, 0],
    activities: [
      test(0, "Математика", "Обыкновенные дроби", 6, 8, "Повторить сравнение дробей."),
      lesson(0, "Математика", "Числитель и знаменатель", "Завершена лекция о дробях."),
      test(1, "Қазақ тілі", "Септік жалғаулары", 5, 8, "Повторить падежные окончания."),
      test(2, "Математика", "Доли и дроби", 6, 8, "Повторить выделение доли числа."),
      lesson(2, "Қазақ тілі", "Окончания слов", "Завершена лекция с примерами."),
      test(4, "История Казахстана", "Древний Казахстан", 5, 8, "Повторить хронологию."),
      test(4, "Қазақ тілі", "Окончания в тексте", 6, 8, "Повторить согласование слов."),
      test(5, "Математика", "Задачи на дроби", 5, 8, "Полезно выделять главное в условии."),
      test(5, "История Казахстана", "Исторические даты", 5, 8, "Повторить последовательность событий.")
    ]
  },
  aiya: {
    minutes: [25, 0, 20, 0, 30, 0, 0],
    activities: [
      test(0, "Математика", "Отношения", 5, 8, "Повторить определение отношения чисел."),
      lesson(0, "Математика", "Отношения величин", "Завершена лекция с примерами."),
      test(2, "История Казахстана", "События Средневековья", 4, 8, "Повторить последовательность событий."),
      test(4, "Қазақ тілі", "Времена глагола", 5, 8, "Повторить формы глаголов."),
      lesson(4, "История Казахстана", "Средневековый Казахстан", "Завершена обзорная лекция.")
    ]
  }
};

const $ = (selector) => document.querySelector(selector);
const escapeHtml = (value) => String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
const duration = (minutes) => minutes >= 60 ? `${Math.floor(minutes / 60)} ч${minutes % 60 ? ` ${minutes % 60} мин` : ""}` : `${minutes} мин`;
const plural = (n, forms) => forms[n % 100 >= 11 && n % 100 <= 14 ? 2 : n % 10 === 1 ? 0 : n % 10 >= 2 && n % 10 <= 4 ? 1 : 2];
const count = (n, forms) => `${n} ${plural(n, forms)}`;
const icon = (kind) => kind === "test" ? '<svg class="lucide-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5H5v16h14V5h-4M9 3h6v4H9zM8 14l3 3 5-6"/></svg>' : '<svg class="lucide-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v16M12 5C9 3 5 3 3 4v15c3-1 6-1 9 2 3-3 6-3 9-2V4c-2-1-6-1-9 1Z"/></svg>';

function getReport(child, week) {
  if (week === "current") return currentReports[child];
  if (week === "previous") return previousReports[child];
  if (week === "first") return { minutes: [0, 0, 0, 0, 15, 0, 0], activities: [test(4, "Математика", "Первое знакомство с дробями", 6, 8, "Первый результат сохранён. Для выводов о динамике нужно больше занятий.")] };
  return { minutes: [0, 0, 0, 0, 0, 0, 0], activities: [] };
}

function totals(report) {
  const tests = report.activities.filter((activity) => activity.type === "test");
  const questions = tests.reduce((sum, activity) => sum + activity.total, 0);
  const correct = tests.reduce((sum, activity) => sum + activity.correct, 0);
  return { tests: tests.length, lessons: report.activities.length - tests.length, questions, correct,
    percent: questions ? Math.round(100 * correct / questions) : null,
    minutes: report.minutes.reduce((a, b) => a + b, 0), days: report.minutes.filter(Boolean).length };
}

// В localStorage остаются только флаги макета; телефон, имя и токены не сохраняются.
const storageKey = "damulab-parent-report-mockup-v1";
function readSettings() {
  try { return JSON.parse(localStorage.getItem(storageKey)) || {}; } catch { return {}; }
}
function notify(message) {
  $("#toast").textContent = message;
  $("#toast").hidden = false;
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => { $("#toast").hidden = true; }, 4000);
}

function renderReportPage() {
  const params = new URLSearchParams(location.search);
  let child = Object.hasOwn(children, params.get("child")) ? params.get("child") : "amir";
  let week = Object.hasOwn(weeks, params.get("week")) ? params.get("week") : "current";
  let filter = "all";
  const order = Object.keys(weeks);

  function render() {
    const profile = children[child];
    const report = getReport(child, week);
    const stats = totals(report);
    const baseline = week === "current" ? totals(previousReports[child]) : null;
    const difference = baseline ? stats.percent - baseline.percent : null;
    document.title = `Отчёт ${profile.reportName} · ${weeks[week].label} · Damulab.kz`;
    $("#week").value = week;
    $("#previous-week").disabled = order.indexOf(week) === order.length - 1;
    $("#next-week").disabled = order.indexOf(week) === 0;
    document.querySelectorAll("[data-child]").forEach((button) => {
      const selected = button.dataset.child === child;
      button.classList.toggle("active", selected);
      button.setAttribute("aria-pressed", String(selected));
    });
    const settings = readSettings();
    const deliveryActive = settings.enabled && Array.isArray(settings.children) && settings.children.includes(child);
    const delivery = `<section class="panel report-panel"><h2>Отчёт сам найдёт вас</h2><p>Главное за неделю в Telegram или WhatsApp. Без необходимости заходить в кабинет.</p><div class="report-delivery-item"><span class="report-channel-icon telegram" aria-hidden="true">➤</span><span><strong>Telegram</strong><small>${deliveryActive && settings.telegram ? "Включён в деморежиме" : "Не настроен для ребёнка"}</small></span></div><div class="report-delivery-item"><span class="report-channel-icon whatsapp" aria-hidden="true">◔</span><span><strong>WhatsApp</strong><small>${deliveryActive && settings.whatsapp ? "Включён в деморежиме" : "Не настроен для ребёнка"}</small></span></div><a class="button primary" href="delivery.html">${deliveryActive ? "Настроить рассылку" : "Получать отчёты"}</a></section>`;
    const sidebar = `<aside class="report-sidebar"><section class="panel report-panel report-tip"><p class="report-tip-label">КАК ПОДДЕРЖАТЬ РЕБЁНКА</p><h2>${week === "current" ? profile.tip : "Начать с небольшого шага"}</h2><p>${week === "current" ? profile.advice : "Выберите вместе знакомую тему и предложите короткое занятие. По одной неделе нельзя судить о способностях ребёнка."}</p><blockquote>${week === "current" ? profile.phrase : "«Давай выберем, что тебе интересно попробовать сегодня»."}</blockquote></section>${delivery}<section class="panel report-panel"><h2>Что означают цифры</h2><p>Верные ответы — доля правильных ответов во всех завершённых тестах за выбранную неделю.</p><p>Динамика сравнивает эту долю с предыдущей неделей. Это результат практики, а не школьная оценка.</p></section></aside>`;
    if (!stats.tests && !stats.lessons) {
      $("#report-content").innerHTML = `<div class="report-main"><section class="report-hero"><span class="badge">${weeks[week].full}</span><h2>У каждого свой ритм</h2><p>Отчёт ${profile.reportName} за выбранную неделю.</p></section><section class="panel report-panel report-empty"><span class="report-empty-symbol" aria-hidden="true">☀</span><h2>На этой неделе занятий не было</h2><p>В этот период нет завершённых тестов и лекций. Пауза — тоже часть обучения. Можно спокойно вернуться к любимой теме.</p><p>Нет результатов для оценки прогресса. Сравнение с другими неделями появится после занятий.</p><button class="button primary" type="button" id="show-latest">Посмотреть последнюю неделю</button></section></div>${sidebar}`;
      $("#show-latest").addEventListener("click", () => { week = "current"; update(); });
      return;
    }
    const accuracyComparison = difference === null ? "Нет сравнения с прошлой неделей" : `${difference > 0 ? "+" : ""}${difference} п. п. к прошлой неделе`;
    const heroTitle = week === "current" ? `${profile.name} ${child === "amir" ? "стал увереннее" : "стала увереннее"} в своих знаниях` : week === "first" ? "Первый шаг уже сделан" : "Неделя практики и новых открытий";
    const heroText = week === "first" ? "Первый тест — отправная точка. Пока рано делать выводы о динамике: следующие занятия помогут увидеть, что уже получается и где нужна поддержка." : `За неделю — ${count(stats.tests, ["завершённый тест", "завершённых теста", "завершённых тестов"])} и ${count(stats.lessons, ["лекция", "лекции", "лекций"])}. ${week === "current" ? "Правильных ответов стало больше, чем на прошлой неделе. Хороший повод отметить старания!" : "Каждое занятие помогает лучше понять, что уже получается."}`;
    const days = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"].map((day, index) => `<div class="report-day ${report.minutes[index] ? "" : "rest"}"><span>${day}, ${weeks[week].dates[index]}</span><b>${report.minutes[index] || "—"}</b><small>${report.minutes[index] ? "минут" : "без занятий"}</small></div>`).join("");
    const subjects = [...new Set(report.activities.filter((item) => item.type === "test").map((item) => item.subject))].map((subject, index) => {
      const rows = report.activities.filter((item) => item.type === "test" && item.subject === subject);
      const correct = rows.reduce((sum, row) => sum + row.correct, 0);
      const total = rows.reduce((sum, row) => sum + row.total, 0);
      const percent = Math.round(100 * correct / total);
      const previousRows = baseline ? previousReports[child].activities.filter((item) => item.type === "test" && item.subject === subject) : [];
      const previousTotal = previousRows.reduce((sum, row) => sum + row.total, 0);
      const delta = previousTotal ? percent - Math.round(100 * previousRows.reduce((sum, row) => sum + row.correct, 0) / previousTotal) : null;
      return `<div class="report-subject"><div class="report-subject-head"><strong>${subject}</strong><span>${percent}%${delta === null ? "" : ` <span class="report-trend">· ${delta > 0 ? "+" : ""}${delta} п. п.</span>`}</span></div><p>${count(rows.length, ["тест", "теста", "тестов"])} · верно ${correct} из ${total}</p><div class="report-progress ${["", "mint", "amber"][index % 3]}" role="img" aria-label="${subject}: ${percent}% верных ответов"><span style="width:${percent}%"></span></div></div>`;
    }).join("");
    $("#report-content").innerHTML = `<div class="report-main"><section class="report-hero"><div class="report-hero-top"><span class="badge success">Итоги недели</span><span>${weeks[week].full}</span></div><h2>${heroTitle}</h2><p>${heroText}</p></section><dl class="report-stats"><div class="report-stat"><dt>Время занятий</dt><dd>${duration(stats.minutes)}</dd><small>${count(stats.days, ["учебный день", "учебных дня", "учебных дней"])} из 7</small></div><div class="report-stat"><dt>Ответов на вопросы</dt><dd>${stats.questions}</dd><small>${stats.correct} верных из ${stats.questions}</small></div><div class="report-stat"><dt>Верных ответов</dt><dd>${stats.percent}<small>%</small></dd><small class="${difference !== null && difference > 0 ? "report-trend" : ""}">${accuracyComparison}</small></div></dl><section class="panel report-panel"><div class="report-section-heading"><h2>Ритм недели</h2><small>${count(stats.days, ["день", "дня", "дней"])} с занятиями</small></div><div class="report-days">${days}</div><p class="report-hint">Время в лекциях и тестах. День без занятий не обнуляет прогресс.</p></section><section class="panel report-panel"><div class="report-section-heading"><h2>Как идут дела по предметам</h2><small>Верные ответы в тестах</small></div>${baseline ? '<p class="report-legend">Изменение в п. п. по сравнению с прошлой неделей</p>' : ""}${subjects}</section><section class="panel report-panel"><div class="report-section-heading"><h2>Что сделано за неделю</h2><small>${report.activities.length} занятий</small></div><div class="report-filter-tabs" role="group" aria-label="Тип занятия"><button type="button" data-filter="all">Все (${report.activities.length})</button><button type="button" data-filter="lesson">Лекции (${stats.lessons})</button><button type="button" data-filter="test">Тесты (${stats.tests})</button></div><div id="activity-list"></div></section></div>${sidebar}`;
    document.querySelectorAll("[data-filter]").forEach((button) => button.addEventListener("click", () => { filter = button.dataset.filter; renderActivities(); }));
    renderActivities();
  }

  function renderActivities() {
    const report = getReport(child, week);
    document.querySelectorAll("[data-filter]").forEach((button) => {
      button.classList.toggle("active", button.dataset.filter === filter);
      button.setAttribute("aria-pressed", String(button.dataset.filter === filter));
    });
    const visible = report.activities.filter((item) => filter === "all" || item.type === filter).slice().sort((a, b) => b.day - a.day);
    $("#activity-list").innerHTML = visible.length ? visible.map((item) => `<article class="report-activity"><span class="report-activity-icon ${item.type}">${icon(item.type)}</span><div class="report-activity-body"><strong>${item.title}</strong><p>${item.subject} · ${item.type === "test" ? "Тест" : "Лекция"} · ${weeks[week].dates[item.day]} ${week === "first" && item.day < 3 ? "сентября" : "октября"}</p><details><summary>${item.type === "test" ? "Что получилось и что повторить" : "Что изучено"}</summary><p>${item.detail}</p></details></div><span class="report-activity-result">${item.type === "test" ? `${item.correct}/${item.total}` : "Изучено ✓"}</span></article>`).join("") : '<p class="report-muted">За эту неделю нет занятий выбранного типа.</p>';
  }

  function update() {
    filter = "all";
    // Прямую ссылку можно открыть из макета сообщения. file:// не всегда разрешает History API.
    try { history.replaceState(null, "", `?child=${child}&week=${week}`); } catch { /* Выбор продолжает работать при открытии с диска. */ }
    render();
  }
  document.querySelectorAll("[data-child]").forEach((button) => button.addEventListener("click", () => { child = button.dataset.child; update(); }));
  $("#week").addEventListener("change", (event) => { week = event.target.value; update(); });
  $("#previous-week").addEventListener("click", () => { week = order[Math.min(order.length - 1, order.indexOf(week) + 1)]; update(); });
  $("#next-week").addEventListener("click", () => { week = order[Math.max(0, order.indexOf(week) - 1)]; update(); });
  render();
}

function renderDeliveryPage() {
  const saved = readSettings();
  let telegram = Boolean(saved.telegram);
  let whatsapp = Boolean(saved.whatsapp);
  let connecting = "telegram";
  let preview = "telegram";
  let messageText = "";
  let enabled = Boolean(saved.enabled);
  if (Array.isArray(saved.children)) document.querySelectorAll('[name="children"]').forEach((input) => { input.checked = saved.children.includes(input.value); });
  if (["monday", "sunday"].includes(saved.day)) $("#delivery-day").value = saved.day;
  if (/^\d{2}:\d{2}$/.test(saved.time || "")) $("#delivery-time").value = saved.time;
  $("#delivery-consent").checked = saved.consent !== false;

  function updateChannels() {
    for (const [channel, connected] of [["telegram", telegram], ["whatsapp", whatsapp]]) {
      $(`#${channel}-channel`).classList.toggle("connected", connected);
      $(`#${channel}-state`).textContent = connected ? "Подключён · деморежим" : "Не подключён";
      $(`#connect-${channel}`).textContent = connected ? "Отключить" : "Подключить";
    }
  }
  function updateSummary() {
    $("#pause-delivery").hidden = !enabled;
    const settings = readSettings();
    $("#delivery-summary").textContent = enabled ? `Сохранено в макете: ${settings.day === "sunday" ? "по воскресеньям" : "по понедельникам"}, ${settings.time || "09:00"} (UTC+5). ${count(settings.children?.length || 0, ["ребёнок", "ребёнка", "детей"])}. Реальная отправка не подключена.` : "Рассылка отключена. Для включения выберите детей и мессенджер, затем сохраните настройки.";
  }
  function renderMessage() {
    const child = $("#preview-child").value;
    const profile = children[child];
    const stats = totals(currentReports[child]);
    const delta = stats.percent - totals(previousReports[child]).percent;
    const intro = child === "amir" ? "Амир занимался 5 дней и стал увереннее в своих знаниях." : "Айя занималась 4 дня и стала увереннее в своих знаниях.";
    const achievement = child === "amir" ? "Получается лучше: окончания в казахском языке — 7 из 8 верных ответов в последнем тесте." : "Получается лучше: пропорции — 7 из 8 верных ответов.";
    const advice = child === "amir" ? "Попробуйте вместе разобрать одну текстовую задачу. Попросите Амира сначала пересказать условие своими словами." : "Нарисуйте вместе короткую линию времени из трёх исторических событий и обсудите их порядок.";
    const parts = [`🌱 Неделя ${profile.reportName} в Damulab`, weeks.current.full, intro,
      `📚 ${count(stats.lessons, ["лекция", "лекции", "лекций"])} и ${count(stats.tests, ["тест", "теста", "тестов"])}\n⏱ ${duration(stats.minutes)} занятий\n✍️ ${stats.correct} верных ответов из ${stats.questions}\n📈 ${stats.percent}% верных ответов (${delta > 0 ? "+" : ""}${delta} п. п. к прошлой неделе)`, achievement, `💛 Как поддержать\n${advice}`, "Отметьте старания — маленькие шаги складываются в большой прогресс."];
    $("#message-body").innerHTML = parts.map((part, index) => `<p>${index === 0 ? "<strong>" : ""}${escapeHtml(part).replace(/\n/g, "<br>")}${index === 0 ? "</strong>" : ""}</p>`).join("");
    $("#message-link").href = `index.html?child=${child}&week=current`;
    // При копировании не подставляем фиктивный публичный URL: макет ещё не опубликован.
    messageText = `${parts.join("\n\n")}\n\nПолный отчёт — в кабинете родителя Damulab.`;
    $("#message-preview").className = `report-messenger ${preview}-preview`;
    $("#messenger-subtitle").textContent = preview === "telegram" ? "бот · еженедельные отчёты" : "еженедельные отчёты";
    document.querySelectorAll("[data-preview]").forEach((button) => {
      button.classList.toggle("active", button.dataset.preview === preview);
      button.setAttribute("aria-pressed", String(button.dataset.preview === preview));
    });
  }
  document.querySelectorAll("[data-preview]").forEach((button) => button.addEventListener("click", () => { preview = button.dataset.preview; renderMessage(); }));
  $("#preview-child").addEventListener("change", renderMessage);
  for (const channel of ["telegram", "whatsapp"]) {
    $(`#connect-${channel}`).addEventListener("click", () => {
      if ((channel === "telegram" && telegram) || (channel === "whatsapp" && whatsapp)) {
        if (channel === "telegram") telegram = false; else whatsapp = false;
        updateChannels();
        notify("Канал отключён в форме. Сохраните настройки, чтобы применить изменение.");
        return;
      }
      connecting = channel;
      $("#connect-title").textContent = `Подключить ${channel === "telegram" ? "Telegram" : "WhatsApp"}`;
      $("#connect-description").textContent = channel === "telegram" ? "В готовом сервисе вы перейдёте в бота Damulab и подтвердите получение отчётов в своём Telegram." : "В готовом сервисе вы укажете номер и подтвердите получение отчётов в WhatsApp.";
      $("#phone-field").hidden = channel !== "whatsapp";
      $("#whatsapp-phone").required = channel === "whatsapp";
      $("#connect-dialog").showModal();
    });
  }
  $("#close-connect").addEventListener("click", () => $("#connect-dialog").close());
  // Возвращаем фокус к инициатору и после Escape, и после подтверждения подключения.
  $("#connect-dialog").addEventListener("close", () => $(`#connect-${connecting}`).focus({ preventScroll: true }));
  $("#whatsapp-phone").addEventListener("input", () => $("#whatsapp-phone").setCustomValidity(""));
  $("#connect-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (connecting === "whatsapp" && !/^\+?[1-9]\d{9,14}$/.test($("#whatsapp-phone").value.replace(/[\s()-]/g, ""))) {
      $("#whatsapp-phone").setCustomValidity("Введите номер с кодом страны, например +7 700 123 45 67.");
      $("#whatsapp-phone").reportValidity();
      return;
    }
    if (connecting === "telegram") telegram = true; else whatsapp = true;
    $("#whatsapp-phone").value = "";
    $("#connect-dialog").close();
    updateChannels();
    notify("Подключение показано в деморежиме. Сохраните настройки.");
  });
  $("#delivery-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const selected = [...document.querySelectorAll('[name="children"]:checked')].map((input) => input.value);
    const error = !selected.length ? "Выберите хотя бы одного ребёнка." : !telegram && !whatsapp ? "Подключите хотя бы один мессенджер в деморежиме." : !$("#delivery-consent").checked ? "Подтвердите, что хотите получать отчёты." : "";
    $("#form-error").textContent = error;
    $("#form-error").hidden = !error;
    if (error) { $("#form-error").scrollIntoView({ block: "center" }); return; }
    const settings = { enabled: true, children: selected, telegram, whatsapp, day: $("#delivery-day").value, time: $("#delivery-time").value, consent: true };
    try { localStorage.setItem(storageKey, JSON.stringify(settings)); } catch { notify("Браузер запретил локальное сохранение. Настройки не сохранены."); return; }
    enabled = true;
    updateSummary();
    notify("Настройки сохранены в этом браузере. Это демонстрация рассылки.");
  });
  $("#pause-delivery").addEventListener("click", () => {
    const settings = readSettings();
    try { localStorage.setItem(storageKey, JSON.stringify({ ...settings, enabled: false })); } catch { notify("Не удалось сохранить изменение в браузере."); return; }
    enabled = false;
    updateSummary();
    notify("Рассылка отключена в макете.");
  });
  $("#copy-message").addEventListener("click", async () => {
    try { await navigator.clipboard.writeText(messageText); notify("Текст отчёта скопирован."); }
    catch {
      const selection = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents($("#message-body"));
      selection.removeAllRanges(); selection.addRange(range);
      notify("Копирование недоступно. Текст выделен — нажмите Ctrl+C.");
    }
  });
  updateChannels();
  updateSummary();
  renderMessage();
}

if (document.body.dataset.page === "report") renderReportPage();
if (document.body.dataset.page === "delivery") renderDeliveryPage();
