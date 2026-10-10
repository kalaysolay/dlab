(() => {
    const parameters = new URLSearchParams(window.location.search);

    function markSelected(container, control) {
        container.querySelectorAll("[data-value]").forEach((item) => {
            const selected = item === control;
            item.classList.toggle("is-selected", selected);
            item.setAttribute("aria-checked", String(selected));
        });
    }

    document.querySelectorAll("[data-select-group]").forEach((group) => {
        group.addEventListener("click", (event) => {
            const control = event.target.closest("[data-value]");
            if (!control || control.disabled || control.classList.contains("is-unavailable")) {
                return;
            }
            markSelected(group, control);
            group.dispatchEvent(new CustomEvent("flow-selection", {bubbles: true}));
        });
    });

    const wordsPage = document.querySelector("[data-words-setup]");
    if (wordsPage) {
        const play = document.querySelector("[data-play-link]");
        const summary = document.querySelector("[data-start-summary]");
        const update = () => {
            const pair = document.querySelector("[data-select-group='pair'] .is-selected");
            const count = document.querySelector("[name='wordCount']")?.value || "10";
            const seconds = document.querySelector("[name='wordSeconds']")?.value || "15";
            if (!pair) {
                play.setAttribute("aria-disabled", "true");
                play.href = "#";
                summary.innerHTML = "<strong>Выберите язык</strong><span>Затем можно сразу начинать.</span>";
                return;
            }
            play.removeAttribute("aria-disabled");
            play.href = `word-game.html?pair=${pair.dataset.value}&count=${count}&seconds=${seconds}`;
            summary.innerHTML = `<strong>${pair.dataset.label}</strong><span>${count} слов · ${seconds} секунд на слово</span>`;
        };
        wordsPage.addEventListener("flow-selection", update);
        document.querySelectorAll("[name='wordCount'], [name='wordSeconds']").forEach((input) => input.addEventListener("change", update));
        update();
    }

    const subjectsPage = document.querySelector("[data-subjects-setup]");
    if (subjectsPage) {
        const play = document.querySelector("[data-play-link]");
        const summary = document.querySelector("[data-start-summary]");

        const updateAvailability = () => {
            const grade = document.querySelector("[data-select-group='grade'] .is-selected")?.dataset.value || "5";
            document.querySelectorAll("[data-select-group='subject'] [data-value]").forEach((subject) => {
                const available = subject.dataset.grades.split(",").includes(grade);
                subject.classList.toggle("is-unavailable", !available);
                subject.setAttribute("aria-disabled", String(!available));
                if (!available && subject.classList.contains("is-selected")) {
                    subject.classList.remove("is-selected");
                    subject.setAttribute("aria-checked", "false");
                }
            });
        };

        const update = () => {
            updateAvailability();
            const grade = document.querySelector("[data-select-group='grade'] .is-selected");
            const subject = document.querySelector("[data-select-group='subject'] .is-selected:not(.is-unavailable)");
            const count = document.querySelector("[name='questionCount']")?.value || "10";
            const seconds = document.querySelector("[name='questionSeconds']")?.value || "20";
            if (!subject) {
                play.setAttribute("aria-disabled", "true");
                play.href = "#";
                summary.innerHTML = `<strong>${grade?.dataset.label || "5 класс"}</strong><span>Выберите предмет.</span>`;
                return;
            }
            play.removeAttribute("aria-disabled");
            play.href = `subject-game.html?subject=${subject.dataset.value}&grade=${grade.dataset.value}&count=${count}&seconds=${seconds}`;
            summary.innerHTML = `<strong>${subject.dataset.label} · ${grade.dataset.label}</strong><span>${count} вопросов · ${seconds} секунд на вопрос</span>`;
        };

        subjectsPage.addEventListener("flow-selection", update);
        document.querySelectorAll("[name='questionCount'], [name='questionSeconds']").forEach((input) => input.addEventListener("change", update));
        update();
    }

    const resultRoot = document.querySelector("[data-result-page]");
    if (resultRoot) {
        const score = Number(parameters.get("score") || 8);
        const total = Number(parameters.get("total") || 10);
        const returnPage = parameters.get("return") || "room.html?game=english";
        const resultType = parameters.get("type") || "words";
        document.querySelector("[data-result-score]").textContent = `${score}/${total}`;
        document.querySelector("[data-result-percent]").textContent = `${Math.round((score / total) * 100)}%`;
        const replay = resultRoot.querySelector(".flow-intro .button.primary");
        replay.href = returnPage;
        replay.textContent = resultType === "subject" ? "Ещё по предмету" : "Играть ещё";
        if (resultType === "subject") {
            const resultSubject = parameters.get("subject") || "math";
            const resultGrade = parameters.get("grade") || "5";
            const repeat = document.querySelector("[data-repeat-link]");
            repeat.href = `subject-game.html?subject=${resultSubject}&grade=${resultGrade}&count=2&seconds=20`;
            document.querySelector("[data-review-list]").innerHTML = [
                "<div class=\"review-row\"><strong>Проценты</strong><span>Ваш ответ: «25» · Верно: «20»</span><span class=\"review-status wrong\">Повторить</span></div>",
                "<div class=\"review-row\"><strong>Дроби</strong><span>Время вышло · Верно: «1/2»</span><span class=\"review-status wrong\">Повторить</span></div>"
            ].join("");
        } else if (resultType === "facts") {
            const repeat = document.querySelector("[data-repeat-link]");
            repeat.href = "true-false-game.html?count=2&seconds=10";
            document.querySelector("[data-review-list]").innerHTML = [
                "<div class=\"review-row\"><strong>Молния</strong><span>Это утверждение неверно: молния может попадать в одно место снова.</span><span class=\"review-status wrong\">Повторить</span></div>",
                "<div class=\"review-row\"><strong>Кипение воды</strong><span>Температура кипения зависит от давления.</span><span class=\"review-status wrong\">Повторить</span></div>"
            ].join("");
        }
    }

    const roomRoot = document.querySelector("[data-room]");
    if (roomRoot) {
        const gameKey = parameters.get("game") || "english";
        const guestMode = parameters.get("mode") === "guest";
        const roomCode = parameters.get("code") || "QZ7K2M";
        const roomGames = {
            english: {type: "Слова", title: "English: слова", description: "Выбирайте правильный перевод английских слов.", icon: "languages", countLabel: "Слов", secondsLabel: "Время на слово", defaultCount: "10", defaultSeconds: "15", note: "В игре используются только проверенные слова подходящего уровня.", startPage: "word-game.html?pair=en-ru"},
            kazakh: {type: "Слова", title: "Қазақша: слова", description: "Читайте слово на казахском и находите перевод.", icon: "book-open-check", countLabel: "Слов", secondsLabel: "Время на слово", defaultCount: "10", defaultSeconds: "15", note: "В игре используются проверенные пары казахских и русских слов.", startPage: "word-game.html?pair=kk-ru"},
            math: {type: "По предмету", title: "Математика", description: "Задачи, дроби и проценты для вашего класса.", icon: "line-chart", countLabel: "Вопросов", secondsLabel: "Время на вопрос", defaultCount: "10", defaultSeconds: "20", grade: true, note: "Вопросы будут подобраны для выбранного класса.", startPage: "subject-game.html?subject=math"},
            facts: {type: "Верю / не верю", title: "Удивительные факты", description: "Решите, правда ли утверждение. Класс выбирать не нужно.", icon: "brain", countLabel: "Фактов", secondsLabel: "Время на факт", defaultCount: "5", defaultSeconds: "10", note: "Каждый факт проверен редактором и содержит однозначный ответ.", startPage: "true-false-game.html"}
        };
        const game = roomGames[gameKey] || roomGames.english;
        const countInput = roomRoot.querySelector("[name='roomCount']");
        const secondsInput = roomRoot.querySelector("[name='roomSeconds']");
        const gradeInput = roomRoot.querySelector("[name='roomGrade']");
        const gradeField = roomRoot.querySelector("[data-grade-field]");
        const startLink = roomRoot.querySelector("[data-room-start]");
        let playerCount = guestMode ? 2 : 1;

        roomRoot.querySelector("[data-room-type]").textContent = game.type;
        roomRoot.querySelector("[data-room-title]").textContent = game.title;
        roomRoot.querySelector("[data-room-description]").textContent = game.description;
        roomRoot.querySelector("[data-room-icon] use").setAttribute("href", `../../../src/main/resources/static/icons/lucide-sprite.svg#${game.icon}`);
        roomRoot.querySelector("[data-count-label]").textContent = game.countLabel;
        roomRoot.querySelector("[data-seconds-label]").textContent = game.secondsLabel;
        roomRoot.querySelector("[data-content-note] span").textContent = game.note;
        roomRoot.querySelector("[data-room-code]").textContent = roomCode.toUpperCase();
        countInput.value = game.defaultCount;
        secondsInput.value = game.defaultSeconds;
        gradeField.hidden = !game.grade;

        if (guestMode) {
            roomRoot.querySelector("[data-player-list]").innerHTML = '<div class="player-row"><span class="player-avatar">А</span><span><strong>Айдар</strong><small>Организатор</small></span><span class="ready-mark">Готов</span></div><div class="player-row"><span class="player-avatar">М</span><span><strong>Малика</strong><small>Вы</small></span><span class="ready-mark">Готова</span></div>';
            roomRoot.querySelectorAll(".room-settings select").forEach((select) => select.disabled = true);
            roomRoot.querySelector("[data-invite-toggle]").hidden = true;
            roomRoot.querySelector("[data-start-question]").textContent = "Вы в игре";
            roomRoot.querySelector(".room-status").innerHTML = '<span class="status-dot"></span> Ждём организатора';
            startLink.textContent = "Ждём запуска…";
            startLink.setAttribute("aria-disabled", "true");
            startLink.removeAttribute("href");
        }

        function updateRoomSummary() {
            const count = countInput.value;
            const seconds = secondsInput.value;
            const peopleWord = playerCount === 1 ? "человек" : "человека";
            const itemWord = gameKey === "facts" ? "фактов" : (gameKey === "english" || gameKey === "kazakh" ? "слов" : "вопросов");
            roomRoot.querySelector("[data-people-count]").textContent = String(playerCount);
            roomRoot.querySelector("[data-people-copy]").textContent = playerCount === 1 ? "Вы пока один — можно начинать." : "Все готовы. Можно начинать.";
            roomRoot.querySelector("[data-start-summary]").textContent = `${count} ${itemWord} · ${seconds} секунд · сейчас играет ${playerCount} ${peopleWord}`;
            if (!guestMode) {
                const separator = game.startPage.includes("?") ? "&" : "?";
                const grade = game.grade ? `&grade=${gradeInput.value}` : "";
                startLink.href = `${game.startPage}${separator}count=${count}&seconds=${seconds}${grade}`;
            }
        }

        roomRoot.querySelectorAll(".room-settings select").forEach((select) => select.addEventListener("change", updateRoomSummary));
        roomRoot.querySelector("[data-invite-toggle]")?.addEventListener("click", () => {
            const box = roomRoot.querySelector("[data-invite-box]");
            box.hidden = !box.hidden;
        });
        roomRoot.querySelector("[data-copy-code]")?.addEventListener("click", async (event) => {
            try {
                await navigator.clipboard.writeText(roomCode.toUpperCase());
                event.currentTarget.textContent = "Скопировано";
            } catch (_error) {
                event.currentTarget.textContent = roomCode.toUpperCase();
            }
        });
        roomRoot.querySelector("[data-simulate-friend]")?.addEventListener("click", (event) => {
            if (playerCount > 1) return;
            playerCount = 2;
            roomRoot.querySelector("[data-player-list]").insertAdjacentHTML("beforeend", '<div class="player-row"><span class="player-avatar">М</span><span><strong>Малика</strong><small>Присоединилась по коду</small></span><span class="ready-mark">Готова</span></div>');
            event.currentTarget.textContent = "Малика присоединилась";
            event.currentTarget.disabled = true;
            updateRoomSummary();
        });
        updateRoomSummary();
    }

    const joinForm = document.querySelector("[data-join-form]");
    if (joinForm) {
        joinForm.addEventListener("submit", (event) => {
            event.preventDefault();
            const code = joinForm.elements.roomCode.value.trim().toUpperCase();
            const error = document.querySelector("[data-join-error]");
            if (!/^[A-ZА-Я0-9]{6}$/u.test(code)) {
                error.hidden = false;
                joinForm.elements.roomCode.focus();
                return;
            }
            error.hidden = true;
            window.location.href = `room.html?game=english&mode=guest&code=${encodeURIComponent(code)}`;
        });
    }

    const adminEditor = document.querySelector("[data-admin-editor]");
    if (adminEditor) {
        const gameKey = parameters.get("game") || (parameters.get("mode") === "new" ? "new" : "math");
        const adminGames = {
            english: {title: "English: слова", description: "Выбирайте правильный перевод английских слов.", type: "words", grade: "none", bank: "English · базовый словарь", bankMeta: "62 опубликованных слова · обновлено сегодня", status: "Опубликована"},
            kazakh: {title: "Қазақша: слова", description: "Читайте слово на казахском и находите перевод.", type: "words", grade: "none", bank: "Қазақша · базовый словарь", bankMeta: "48 опубликованных слов · обновлено сегодня", status: "Опубликована"},
            math: {title: "Математика", description: "Задачи, дроби и проценты для вашего класса.", type: "choice", grade: "required", bank: "Математика · 3–5 классы", bankMeta: "84 опубликованных вопроса · обновлено сегодня", status: "Опубликована"},
            facts: {title: "Удивительные факты", description: "Решите, правда ли утверждение.", type: "facts", grade: "none", bank: "Проверенные факты", bankMeta: "18 фактов · требуется публикация", status: "Черновик"},
            new: {title: "Новая игра", description: "", type: "choice", grade: "none", bank: "Банк ещё не выбран", bankMeta: "Добавьте источник вопросов", status: "Черновик"}
        };
        const config = adminGames[gameKey] || adminGames.math;
        adminEditor.querySelector("[data-admin-title]").textContent = config.title;
        adminEditor.querySelector("[data-admin-name]").value = config.title === "Новая игра" ? "" : config.title;
        adminEditor.querySelector("[data-admin-description]").value = config.description;
        adminEditor.querySelector("[data-admin-type]").value = config.type;
        const gradePolicy = adminEditor.querySelector("[data-grade-policy]");
        const gradeValues = adminEditor.querySelector("[data-grade-values]");
        gradePolicy.value = config.grade;
        const updateGradeFields = () => gradeValues.hidden = gradePolicy.value === "none";
        gradePolicy.addEventListener("change", updateGradeFields);
        updateGradeFields();
        adminEditor.querySelector("[data-admin-bank]").textContent = config.bank;
        adminEditor.querySelector("[data-admin-bank-meta]").textContent = config.bankMeta;
        const status = adminEditor.querySelector("[data-admin-status]");
        status.textContent = config.status;
        status.classList.toggle("published", config.status === "Опубликована");
        status.classList.toggle("draft", config.status !== "Опубликована");
    }

    const quizAdminConfigs = {
        english: {title: "English: слова", status: "Опубликована", type: "words", grade: "none", nameRu: "English: слова", nameKk: "English: сөздер", descriptionRu: "Выбирайте правильный перевод английских слов.", descriptionKk: "Ағылшын сөздерінің дұрыс аудармасын таңдаңыз.", count: "10", seconds: "15"},
        kazakh: {title: "Қазақша: слова", status: "Опубликована", type: "words", grade: "none", nameRu: "Қазақша: слова", nameKk: "Қазақша: сөздер", descriptionRu: "Читайте слово на казахском и находите перевод.", descriptionKk: "Қазақ сөзін оқып, орысша аудармасын табыңыз.", count: "10", seconds: "15"},
        math: {title: "Математика", status: "Опубликована", type: "subject", grade: "required", nameRu: "Математика", nameKk: "Математика", descriptionRu: "Задачи, дроби и проценты для вашего класса.", descriptionKk: "Сыныбыңызға арналған есептер, бөлшектер және пайыздар.", count: "10", seconds: "20"},
        facts: {title: "Удивительные факты", status: "Черновик", type: "facts", grade: "none", nameRu: "Удивительные факты", nameKk: "Таңғажайып деректер", descriptionRu: "Решите, правда ли утверждение.", descriptionKk: "Тұжырымның рас-өтірігін анықтаңыз.", count: "5", seconds: "10"},
        new: {title: "Новая игра", status: "Черновик", type: "words", grade: "none", nameRu: "", nameKk: "", descriptionRu: "", descriptionKk: "", count: "10", seconds: "15"}
    };

    const gameFilter = document.querySelector("[data-game-filter]");
    if (gameFilter) {
        const applyGameFilter = () => {
            const formData = new FormData(gameFilter);
            const type = String(formData.get("type") || "");
            const status = String(formData.get("status") || "");
            const query = String(formData.get("query") || "").trim().toLowerCase();
            let visible = 0;
            document.querySelectorAll("[data-game-row]").forEach((row) => {
                const matches = (!type || row.dataset.type === type) && (!status || row.dataset.status === status) && (!query || row.dataset.search.includes(query));
                row.hidden = !matches;
                if (matches) visible += 1;
            });
            document.querySelector("[data-game-empty]").hidden = visible !== 0;
            document.querySelector("[data-game-table]").hidden = visible === 0;
        };
        gameFilter.addEventListener("submit", (event) => { event.preventDefault(); applyGameFilter(); });
        gameFilter.querySelector("[data-reset-filter]").addEventListener("click", () => { gameFilter.reset(); applyGameFilter(); });
    }

    const quizEditor = document.querySelector("[data-quiz-editor]");
    if (quizEditor) {
        const gameKey = parameters.get("game") || (parameters.get("mode") === "new" ? "new" : "english");
        const config = quizAdminConfigs[gameKey] || quizAdminConfigs.english;
        const status = quizEditor.querySelector("[data-quiz-status]");
        const gradePolicy = quizEditor.querySelector("[data-quiz-grade-policy]");
        const gradeFields = quizEditor.querySelector("[data-quiz-grades]");
        quizEditor.querySelector("[data-quiz-title]").textContent = config.title;
        status.textContent = config.status;
        status.classList.toggle("success", config.status === "Опубликована");
        status.classList.toggle("warning", config.status !== "Опубликована");
        quizEditor.querySelector("[data-name-ru]").value = config.nameRu;
        quizEditor.querySelector("[data-name-kk]").value = config.nameKk;
        quizEditor.querySelector("[data-description-ru]").value = config.descriptionRu;
        quizEditor.querySelector("[data-description-kk]").value = config.descriptionKk;
        quizEditor.querySelector("[data-quiz-type]").value = config.type;
        gradePolicy.value = config.grade;
        quizEditor.querySelector("[data-default-count]").value = config.count;
        quizEditor.querySelector("[data-default-seconds]").value = config.seconds;
        quizEditor.querySelectorAll("[data-tab-link]").forEach((link) => {
            const page = link.dataset.tabLink === "edit" ? "admin-quiz-edit.html" : (link.dataset.tabLink === "content" ? "admin-quiz-content.html" : "admin-quiz-publish.html");
            link.href = `${page}?game=${gameKey}`;
        });
        quizEditor.querySelector("[data-preview-link]").href = `admin-quiz-publish.html?game=${gameKey}`;

        const updateGradePolicy = () => {
            const usesGrade = gradePolicy.value !== "none";
            gradeFields.hidden = !usesGrade;
            quizEditor.querySelector("[data-grade-example-title]").textContent = usesGrade ? "Поле класса появится в комнате" : "Поле класса не появится";
            quizEditor.querySelector("[data-grade-example]").textContent = usesGrade ? "Из профиля подставится 5 класс, если правило разрешает это." : "Ученик сразу увидит остальные настройки комнаты.";
        };
        gradePolicy.addEventListener("change", updateGradePolicy);
        updateGradePolicy();
        quizEditor.querySelectorAll("[data-save-demo]").forEach((button) => button.addEventListener("click", () => { quizEditor.querySelector("[data-save-notice]").hidden = false; }));
    }

    const quizContent = document.querySelector("[data-quiz-content]");
    if (quizContent) {
        const gameKey = parameters.get("game") || "english";
        const config = quizAdminConfigs[gameKey] || quizAdminConfigs.english;
        const contentConfigs = {
            english: {source: "Банк слов", count: 62, published: 58, review: 4, excluded: 11, bank: "English A1 · базовый словарь", link: "admin-word-bank.html", column: "Слово", sample: '<tr><td>W-104</td><td><strong>discover</strong><span class="muted-line">/dɪˈskʌvə/</span></td><td>обнаружить</td><td>10–12 лет</td><td><span class="badge success">published</span></td></tr><tr><td>W-118</td><td><strong>journey</strong><span class="muted-line">/ˈdʒɜːni/</span></td><td>путешествие</td><td>10–12 лет</td><td><span class="badge success">published</span></td></tr><tr><td>W-127</td><td><strong>careful</strong><span class="muted-line">/ˈkeəfəl/</span></td><td>осторожный</td><td>10–12 лет</td><td><span class="badge success">published</span></td></tr>'},
            kazakh: {source: "Банк слов", count: 48, published: 46, review: 2, excluded: 8, bank: "Қазақша · 5 сынып", link: "admin-word-bank.html", column: "Слово", sample: '<tr><td>W-207</td><td><strong>білім</strong></td><td>знание</td><td>10–12 лет</td><td><span class="badge success">published</span></td></tr><tr><td>W-214</td><td><strong>саяхат</strong></td><td>путешествие</td><td>10–12 лет</td><td><span class="badge success">published</span></td></tr><tr><td>W-221</td><td><strong>ұқыпты</strong></td><td>аккуратный</td><td>10–12 лет</td><td><span class="badge warning">review</span></td></tr>'},
            math: {source: "Банк вопросов", count: 84, published: 84, review: 6, excluded: 19, bank: "Математика · 3–5 классы", link: "#", column: "Вопрос", sample: '<tr><td>Q-81</td><td><strong>Чему равно 25% от числа 80?</strong></td><td>20</td><td>5 класс</td><td><span class="badge success">published</span></td></tr><tr><td>Q-94</td><td><strong>Какая дробь равна 0,5?</strong></td><td>1/2</td><td>5 класс</td><td><span class="badge success">published</span></td></tr><tr><td>Q-107</td><td><strong>Периметр квадрата со стороной 6 см</strong></td><td>24 см</td><td>5 класс</td><td><span class="badge success">published</span></td></tr>'},
            facts: {source: "Банк вопросов", count: 18, published: 16, review: 2, excluded: 3, bank: "Факты · TRUE_FALSE", link: "#", column: "Утверждение", sample: '<tr><td>Q-301</td><td><strong>У осьминога три сердца.</strong></td><td>Верю</td><td>10–15 лет</td><td><span class="badge success">published</span></td></tr><tr><td>Q-304</td><td><strong>Молния не попадает дважды в одно место.</strong></td><td>Не верю</td><td>10–15 лет</td><td><span class="badge success">published</span></td></tr><tr><td>Q-309</td><td><strong>Вода всегда кипит при одной температуре.</strong></td><td>Не верю</td><td>10–15 лет</td><td><span class="badge warning">review</span></td></tr>'}
        };
        const content = contentConfigs[gameKey] || contentConfigs.english;
        quizContent.querySelector("[data-content-title]").textContent = config.title;
        quizContent.querySelector("[data-source-type]").textContent = content.source;
        quizContent.querySelector("[data-source-bank]").innerHTML = `<option selected>${content.bank}</option>`;
        quizContent.querySelector("[data-language-direction]").hidden = config.type !== "words";
        quizContent.querySelector("[data-eligibility-label]").textContent = config.grade === "required" ? "Классы" : "Возрастная группа";
        quizContent.querySelector("[data-eligibility-select]").innerHTML = config.grade === "required"
            ? '<option selected>3, 4 и 5 классы</option><option>Только 5 класс</option>'
            : '<option selected>10–12 лет</option><option>7–9 лет</option><option>13–15 лет</option>';
        quizContent.querySelector("[data-content-count]").textContent = String(content.count);
        quizContent.querySelector("[data-content-published]").textContent = String(content.published);
        quizContent.querySelector("[data-content-review]").textContent = String(content.review);
        quizContent.querySelector("[data-content-excluded]").textContent = String(content.excluded);
        quizContent.querySelector("[data-preview-main-column]").textContent = content.column;
        quizContent.querySelector("[data-content-sample]").innerHTML = content.sample;
        quizContent.querySelector("[data-open-source]").href = content.link;
        quizContent.querySelector("[data-open-source]").textContent = config.type === "words" ? "Открыть банк слов" : "Открыть банк вопросов";
        quizContent.querySelectorAll("[data-tab-link]").forEach((link) => {
            const page = link.dataset.tabLink === "edit" ? "admin-quiz-edit.html" : (link.dataset.tabLink === "content" ? "admin-quiz-content.html" : "admin-quiz-publish.html");
            link.href = `${page}?game=${gameKey}`;
        });
        quizContent.querySelectorAll("[data-save-demo]").forEach((button) => button.addEventListener("click", () => { quizContent.querySelector("[data-save-notice]").hidden = false; }));
        quizContent.querySelector("[data-refresh-sample]").addEventListener("click", (event) => { event.currentTarget.textContent = "Пример обновлён"; });
    }

    const quizPublish = document.querySelector("[data-quiz-publish]");
    if (quizPublish) {
        const gameKey = parameters.get("game") || "english";
        const config = quizAdminConfigs[gameKey] || quizAdminConfigs.english;
        const publishConfigs = {
            english: {type: "Слова", icon: "languages", countLabel: "10 слов", content: "58 опубликованных слов подходят под правила.", ready: true},
            kazakh: {type: "Слова", icon: "book-open-check", countLabel: "10 слов", content: "46 опубликованных слов подходят под правила.", ready: true},
            math: {type: "По предмету", icon: "line-chart", countLabel: "10 вопросов", content: "84 опубликованных вопроса подходят под правила.", ready: true},
            facts: {type: "Верю / не верю", icon: "brain", countLabel: "5 фактов", content: "16 фактов опубликованы, 2 ещё требуют проверки.", ready: false}
        };
        const publish = publishConfigs[gameKey] || publishConfigs.english;
        quizPublish.querySelector("[data-publish-title]").textContent = config.title;
        quizPublish.querySelector("[data-preview-title]").textContent = config.nameRu;
        quizPublish.querySelector("[data-preview-description]").textContent = config.descriptionRu;
        quizPublish.querySelector("[data-preview-type]").textContent = publish.type;
        quizPublish.querySelector(".admin-student-game-card use").setAttribute("href", `../../../src/main/resources/static/icons/lucide-sprite.svg#${publish.icon}`);
        quizPublish.querySelector("[data-preview-count]").textContent = publish.countLabel;
        quizPublish.querySelector("[data-preview-seconds]").textContent = `${config.seconds} сек`;
        quizPublish.querySelector("[data-preview-room-count]").textContent = config.count;
        quizPublish.querySelector("[data-preview-room-seconds]").textContent = `${config.seconds} секунд`;
        quizPublish.querySelector("[data-preview-grade-row]").hidden = config.grade === "none";
        quizPublish.querySelector("[data-content-check]").textContent = publish.content;
        quizPublish.querySelector("[data-student-preview-link]").href = `room.html?game=${gameKey}`;
        quizPublish.querySelectorAll("[data-tab-link]").forEach((link) => {
            const page = link.dataset.tabLink === "edit" ? "admin-quiz-edit.html" : (link.dataset.tabLink === "content" ? "admin-quiz-content.html" : "admin-quiz-publish.html");
            link.href = `${page}?game=${gameKey}`;
        });

        const status = quizPublish.querySelector("[data-publish-status]");
        status.textContent = publish.ready ? "Готова" : "Нужна проверка";
        status.classList.toggle("success", publish.ready);
        status.classList.toggle("warning", !publish.ready);
        quizPublish.querySelector("[data-readiness-score]").textContent = publish.ready ? "5 из 5" : "3 из 5";
        quizPublish.querySelectorAll("[data-publish-button]").forEach((button) => {
            button.disabled = !publish.ready;
            button.title = publish.ready ? "" : "Сначала устраните замечания";
            button.addEventListener("click", () => {
                quizPublish.querySelector("[data-publish-notice]").hidden = false;
                status.textContent = "Опубликована";
            });
        });
        if (!publish.ready) {
            const rows = quizPublish.querySelectorAll("[data-check-list] .quiz-check-row");
            [rows[2], rows[3]].forEach((row) => {
                row.classList.remove("is-ok");
                row.classList.add("is-warning");
            });
            rows[3].querySelector("strong").textContent = "Возраст проверен не у всех фактов";
            rows[3].querySelector("div > span").textContent = "У 2 элементов не указана возрастная группа.";
        }
        quizPublish.querySelector("[data-run-check]").addEventListener("click", (event) => { event.currentTarget.textContent = "Проверено только что"; });
    }

    const wordFilter = document.querySelector("[data-word-filter]");
    if (wordFilter) {
        const applyWordFilter = () => {
            const formData = new FormData(wordFilter);
            const language = String(formData.get("language") || "");
            const grade = String(formData.get("grade") || "");
            const status = String(formData.get("status") || "");
            const query = String(formData.get("query") || "").trim().toLowerCase();
            let visible = 0;
            document.querySelectorAll("[data-word-row]").forEach((row) => {
                const matches = (!language || row.dataset.language === language) && (!grade || row.dataset.grade === grade) && (!status || row.dataset.status === status) && (!query || row.dataset.search.includes(query));
                row.hidden = !matches;
                if (matches) visible += 1;
            });
            document.querySelector("[data-word-empty]").hidden = visible !== 0;
            document.querySelector("[data-word-table]").hidden = visible === 0;
        };
        wordFilter.addEventListener("submit", (event) => { event.preventDefault(); applyWordFilter(); });
        wordFilter.querySelector("[data-reset-word-filter]").addEventListener("click", () => { wordFilter.reset(); applyWordFilter(); });
    }

    const wordEditor = document.querySelector("[data-word-editor]");
    if (wordEditor) {
        const wordKey = parameters.get("word") || "new";
        const words = {
            discover: {title: "Редактирование W-104", status: "Опубликовано", language: "en", value: "discover", pronunciation: "/dɪˈskʌvə/", source: "Oxford 3000"},
            journey: {title: "Редактирование W-118", status: "Опубликовано", language: "en", value: "journey", pronunciation: "/ˈdʒɜːni/", source: "Oxford 3000"},
            responsibility: {title: "Проверка W-144", status: "На проверке", language: "en", value: "responsibility", pronunciation: "/rɪˌspɒnsəˈbɪləti/", source: "Cambridge B1"},
            bilim: {title: "Редактирование W-207", status: "Опубликовано", language: "kk", value: "білім", pronunciation: "білім", source: "Оқулық, 5 сынып"},
            uqypty: {title: "Исправление W-221", status: "Есть замечание", language: "kk", value: "ұқыпты", pronunciation: "ұқыпты", source: "Оқулық, 5 сынып"},
            new: {title: "Новое слово", status: "Черновик", language: "en", value: "", pronunciation: "", source: ""}
        };
        const word = words[wordKey] || words.new;
        const status = wordEditor.querySelector("[data-word-editor-status]");
        wordEditor.querySelector("[data-word-editor-title]").textContent = word.title;
        status.textContent = word.status;
        status.className = `badge ${word.status === "Опубликовано" ? "success" : (word.status === "Есть замечание" ? "danger" : "warning")}`;
        wordEditor.querySelector("[data-word-language]").value = word.language;
        wordEditor.querySelector("[data-word-value]").value = word.value;
        wordEditor.querySelector("[data-word-pronunciation]").value = word.pronunciation;
        wordEditor.querySelector("[data-word-source]").value = word.source;

        const translationList = wordEditor.querySelector("[data-translation-list]");
        const wordTranslations = {
            discover: [["обнаружить", "Основное значение"], ["открыть", "Допустимо по контексту"]],
            journey: [["путешествие", "Основное значение"]],
            responsibility: [["ответственность", "Требует проверки уровня"]],
            bilim: [["знание", "Основное значение"], ["образование", "Только в подходящем контексте"]],
            uqypty: [["аккуратный", "Проверить формулировку"], ["опрятный", "Допустимый синоним"]],
            new: [["", ""]]
        };
        translationList.innerHTML = (wordTranslations[wordKey] || wordTranslations.new).map((item, index) => `<div class="translation-row"><span class="option-label">${index + 1}</span><label class="field"><span>Перевод на русском</span><input value="${item[0]}"></label><label class="field"><span>Комментарий</span><input value="${item[1]}"></label><button class="button danger translation-delete" type="button" data-delete-translation>Удалить</button></div>`).join("");
        const reindexTranslations = () => translationList.querySelectorAll(".translation-row").forEach((row, index) => { row.querySelector(".option-label").textContent = String(index + 1); });
        wordEditor.querySelector("[data-add-translation]").addEventListener("click", () => {
            translationList.insertAdjacentHTML("beforeend", '<div class="translation-row"><span class="option-label"></span><label class="field"><span>Перевод на русском</span><input value=""></label><label class="field"><span>Комментарий</span><input value=""></label><button class="button danger translation-delete" type="button" data-delete-translation>Удалить</button></div>');
            reindexTranslations();
        });
        translationList.addEventListener("click", (event) => {
            const remove = event.target.closest("[data-delete-translation]");
            if (!remove || translationList.querySelectorAll(".translation-row").length === 1) return;
            remove.closest(".translation-row").remove();
            reindexTranslations();
        });
        wordEditor.querySelectorAll("[data-save-word]").forEach((button) => button.addEventListener("click", () => { wordEditor.querySelector("[data-word-save-notice]").hidden = false; }));
    }

    const gameRoot = document.querySelector("[data-game]");
    if (!gameRoot) {
        return;
    }

    const wordBanks = {
        "en-ru": [
            {question: "discover", pronunciation: "/dɪˈskʌvə/", answers: ["обнаружить", "обсуждать", "описывать", "решать"], correct: 0},
            {question: "journey", pronunciation: "/ˈdʒɜːni/", answers: ["путешествие", "погода", "задание", "праздник"], correct: 0},
            {question: "careful", pronunciation: "/ˈkeəfəl/", answers: ["осторожный", "быстрый", "громкий", "далёкий"], correct: 0}
        ],
        "kk-ru": [
            {question: "білім", pronunciation: "білім", answers: ["знание", "наука", "школа", "учитель"], correct: 0},
            {question: "саяхат", pronunciation: "саяхат", answers: ["путешествие", "рассказ", "подарок", "урок"], correct: 0},
            {question: "ұқыпты", pronunciation: "ұқыпты", answers: ["аккуратный", "быстрый", "громкий", "далёкий"], correct: 0}
        ]
    };

    const subjectBanks = {
        math: [
            {question: "Чему равно 25% от числа 80?", answers: ["20", "25", "40", "60"], correct: 0, topic: "Проценты"},
            {question: "Какая дробь равна 0,5?", answers: ["1/2", "1/3", "2/5", "3/5"], correct: 0, topic: "Дроби"},
            {question: "Периметр квадрата со стороной 6 см равен…", answers: ["24 см", "12 см", "36 см", "18 см"], correct: 0, topic: "Геометрия"}
        ],
        kazakh: [
            {question: "«Кітап» сөзінің көпше түрін таңдаңыз.", answers: ["кітаптар", "кітапты", "кітапқа", "кітаптан"], correct: 0, topic: "Көптік жалғау"},
            {question: "Зат есімді табыңыз.", answers: ["мектеп", "жақсы", "оқиды", "бес"], correct: 0, topic: "Сөз таптары"},
            {question: "«Үлкен» сөзінің қарама-қарсы мағынасы…", answers: ["кіші", "биік", "ұзын", "кең"], correct: 0, topic: "Антонимдер"}
        ],
        world: [
            {question: "Какой орган помогает человеку дышать?", answers: ["Лёгкие", "Желудок", "Кости", "Кожа"], correct: 0, topic: "Человек"},
            {question: "Что относится к неживой природе?", answers: ["Камень", "Дерево", "Птица", "Гриб"], correct: 0, topic: "Природа"},
            {question: "Как называется спутник Земли?", answers: ["Луна", "Марс", "Солнце", "Венера"], correct: 0, topic: "Космос"}
        ],
        science: [
            {question: "Какое состояние воды имеет собственную форму?", answers: ["Твёрдое", "Жидкое", "Газообразное", "Любое"], correct: 0, topic: "Вещества"},
            {question: "Что измеряют термометром?", answers: ["Температуру", "Длину", "Массу", "Скорость"], correct: 0, topic: "Измерения"},
            {question: "Какой газ нужен человеку для дыхания?", answers: ["Кислород", "Азот", "Водород", "Углекислый газ"], correct: 0, topic: "Воздух"}
        ]
    };

    const factBank = [
        {question: "У осьминога три сердца.", answers: ["Верю", "Не верю"], correct: 0, topic: "Животные"},
        {question: "Молния никогда не попадает в одно место дважды.", answers: ["Верю", "Не верю"], correct: 1, topic: "Природа"},
        {question: "На Луне сила тяжести меньше, чем на Земле.", answers: ["Верю", "Не верю"], correct: 0, topic: "Космос"},
        {question: "Вода всегда кипит при одной и той же температуре.", answers: ["Верю", "Не верю"], correct: 1, topic: "Наука"}
    ];

    const gameType = gameRoot.dataset.game;
    const pair = parameters.get("pair") || "en-ru";
    const subject = parameters.get("subject") || "math";
    const grade = parameters.get("grade") || "5";
    const secondsPerQuestion = Number(parameters.get("seconds") || (gameType === "words" ? 15 : 20));
    const requestedCount = Number(parameters.get("count") || 10);
    const sourceQuestions = gameType === "words" ? wordBanks[pair] : (gameType === "facts" ? factBank : subjectBanks[subject]);
    const questions = Array.from({length: requestedCount}, (_, index) => ({...sourceQuestions[index % sourceQuestions.length]}));
    const typeLabels = {math: "Математика", kazakh: "Қазақский язык", world: "Познание мира", science: "Естествознание"};
    let index = 0;
    let selected = null;
    let locked = false;
    let correctCount = 0;
    let remaining = secondsPerQuestion;
    let timerId;

    const answerRoot = document.querySelector("[data-game-answers]");
    const submit = document.querySelector("[data-game-submit]");
    const skip = document.querySelector("[data-game-skip]");
    const feedback = document.querySelector("[data-game-feedback]");
    const timer = document.querySelector("[data-game-timer]");

    function updateTimer() {
        timer.querySelector("strong").textContent = Math.max(0, remaining);
        timer.classList.toggle("low", remaining <= 5);
    }

    function renderQuestion() {
        const question = questions[index];
        selected = null;
        locked = false;
        remaining = secondsPerQuestion;
        feedback.hidden = true;
        feedback.className = "game-feedback";
        submit.disabled = true;
        submit.textContent = "Ответить";
        skip.disabled = false;

        document.querySelector("[data-game-progress]").style.width = `${((index + 1) / questions.length) * 100}%`;
        document.querySelector("[data-game-progress-copy]").textContent = `${index + 1} из ${questions.length}`;
        document.querySelector("[data-game-question]").textContent = question.question;
        const pronunciation = document.querySelector("[data-game-pronunciation]");
        if (pronunciation) {
            pronunciation.textContent = question.pronunciation;
        }
        const topic = document.querySelector("[data-game-topic]");
        if (topic) {
            topic.textContent = question.topic || "Словарный запас";
        }
        answerRoot.replaceChildren();
        question.answers.forEach((answer, answerIndex) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "game-answer";
            button.dataset.index = answerIndex;
            button.innerHTML = `<span class="game-answer-key">${String.fromCharCode(65 + answerIndex)}</span><span class="game-answer-text"></span>`;
            button.querySelector(".game-answer-text").textContent = answer;
            button.addEventListener("click", () => choose(answerIndex));
            answerRoot.appendChild(button);
        });
        updateTimer();
        window.clearInterval(timerId);
        timerId = window.setInterval(() => {
            remaining -= 1;
            updateTimer();
            if (remaining <= 0) {
                window.clearInterval(timerId);
                reveal(null, "Время вышло.");
            }
        }, 1000);
    }

    function choose(answerIndex) {
        if (locked) {
            return;
        }
        selected = answerIndex;
        answerRoot.querySelectorAll(".game-answer").forEach((button) => button.classList.toggle("selected", Number(button.dataset.index) === answerIndex));
        submit.disabled = false;
    }

    function reveal(answerIndex, prefix = "") {
        if (locked) {
            index += 1;
            if (index >= questions.length) {
                finish();
            } else {
                renderQuestion();
            }
            return;
        }
        locked = true;
        window.clearInterval(timerId);
        const question = questions[index];
        const isCorrect = answerIndex === question.correct;
        if (isCorrect) {
            correctCount += 1;
        }
        answerRoot.querySelectorAll(".game-answer").forEach((button) => {
            const answerButtonIndex = Number(button.dataset.index);
            button.disabled = true;
            button.classList.remove("selected");
            button.classList.toggle("correct", answerButtonIndex === question.correct);
            button.classList.toggle("wrong", answerButtonIndex === answerIndex && !isCorrect);
        });
        feedback.hidden = false;
        feedback.classList.add(isCorrect ? "correct" : "wrong");
        feedback.textContent = isCorrect ? "Верно!" : `${prefix} Правильный ответ: ${question.answers[question.correct]}`;
        submit.disabled = false;
        submit.textContent = index + 1 === questions.length ? "Показать результат" : "Следующий вопрос";
        skip.disabled = true;
    }

    function finish() {
        window.clearInterval(timerId);
        const resultType = gameType === "words" ? "words" : (gameType === "facts" ? "facts" : "subject");
        const returnPage = gameType === "facts" ? "room.html?game=facts" : (gameType === "words" ? `room.html?game=${pair === "kk-ru" ? "kazakh" : "english"}` : "room.html?game=math");
        window.location.href = `result.html?type=${resultType}&score=${correctCount}&total=${questions.length}&return=${returnPage}&pair=${pair}&subject=${subject}&grade=${grade}`;
    }

    submit.addEventListener("click", () => reveal(selected));
    skip.addEventListener("click", () => reveal(null, "Вопрос пропущен."));
    document.addEventListener("keydown", (event) => {
        const answerIndex = ["a", "b", "c", "d"].indexOf(event.key.toLowerCase());
        if (answerIndex >= 0) {
            choose(answerIndex);
        }
        if (event.key === "Enter" && !submit.disabled) {
            submit.click();
        }
    });
    const listen = document.querySelector("[data-game-listen]");
    listen?.addEventListener("click", () => {
        if (!("speechSynthesis" in window)) {
            return;
        }
        const utterance = new SpeechSynthesisUtterance(questions[index].question);
        utterance.lang = pair === "kk-ru" ? "kk-KZ" : "en-US";
        window.speechSynthesis.cancel();
        window.speechSynthesis.speak(utterance);
    });

    document.querySelector("[data-game-label]").textContent = gameType === "words"
        ? (pair === "kk-ru" ? "Қазақша → Русский" : "Английский → Русский")
        : (gameType === "facts" ? "Удивительные факты" : `${typeLabels[subject]} · ${grade} класс`);
    renderQuestion();

})();
