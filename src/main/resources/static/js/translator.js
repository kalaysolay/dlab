(() => {
    const root = document.getElementById("translator-app");
    if (!root) {
        return;
    }

    const source = document.getElementById("translator-source");
    const result = document.getElementById("translator-result");
    const count = document.getElementById("translator-count");
    const translateButton = document.getElementById("translator-submit");
    const explainButton = document.getElementById("translator-explain");
    const explainLabel = explainButton.querySelector("span");
    const explanation = document.getElementById("translator-explanation");
    const explanationText = document.getElementById("translator-explanation-text");
    const error = document.getElementById("translator-error");
    const directionInputs = [...root.querySelectorAll('input[name="direction"]')];
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "X-CSRF-TOKEN";
    const idleTranslateLabel = translateButton.textContent.trim();
    const idleExplainLabel = explainLabel.textContent.trim();
    let lastTranslation = null;

    const renderExplanation = (markdown) => {
        // Ответ LLM недоверенный: Marked отвечает только за форматирование,
        // а DOMPurify удаляет HTML, ссылки, изображения и любые атрибуты.
        if (typeof window.marked?.parse !== "function" || typeof window.DOMPurify?.sanitize !== "function") {
            explanationText.classList.add("is-plain-text");
            explanationText.textContent = markdown;
            return;
        }
        const html = window.marked.parse(markdown, { gfm: true, breaks: true, async: false });
        explanationText.classList.remove("is-plain-text");
        explanationText.innerHTML = window.DOMPurify.sanitize(html, {
            ALLOWED_TAGS: ["p", "br", "strong", "em", "ul", "ol", "li", "h3", "h4", "blockquote", "code"],
            ALLOWED_ATTR: []
        });
    };

    const direction = () => root.querySelector('input[name="direction"]:checked').value;

    // Textarea растёт вслед за коротким текстом, а после мобильного лимита прокручивается вертикально.
    const autoGrow = (textarea) => {
        textarea.style.height = "auto";
        textarea.style.height = `${textarea.scrollHeight}px`;
    };

    const hideError = () => {
        error.hidden = true;
        error.textContent = "";
    };

    const showError = (message) => {
        error.textContent = message;
        error.hidden = false;
    };

    const resetDerivedContent = () => {
        if (!lastTranslation) {
            return;
        }
        lastTranslation = null;
        result.value = "";
        result.style.height = "";
        explainButton.disabled = true;
        explanation.hidden = true;
        explanationText.replaceChildren();
        explanationText.classList.remove("is-plain-text");
    };

    const setTranslateBusy = (busy) => {
        root.setAttribute("aria-busy", String(busy));
        translateButton.disabled = busy;
        source.disabled = busy;
        directionInputs.forEach((input) => input.disabled = busy);
        translateButton.textContent = busy ? root.dataset.translating : idleTranslateLabel;
    };

    const postJson = async (url, body) => {
        const response = await fetch(url, {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                ...(csrfToken ? { [csrfHeader]: csrfToken } : {})
            },
            body: JSON.stringify(body)
        });
        if (!response.ok) {
            throw new Error(`Translator API returned ${response.status}`);
        }
        return response.json();
    };

    source.addEventListener("input", () => {
        count.textContent = String(source.value.length);
        autoGrow(source);
        hideError();
        resetDerivedContent();
    });
    directionInputs.forEach((input) => input.addEventListener("change", () => {
        hideError();
        resetDerivedContent();
    }));

    translateButton.addEventListener("click", async () => {
        const text = source.value.trim();
        if (!text) {
            showError(root.dataset.errorEmpty);
            source.focus();
            return;
        }
        hideError();
        setTranslateBusy(true);
        const request = { direction: direction(), text };
        try {
            const payload = await postJson("/api/student/translator/translate", request);
            result.value = payload.text;
            autoGrow(result);
            lastTranslation = {
                direction: request.direction,
                sourceText: text,
                translatedText: payload.text
            };
            explainButton.disabled = false;
        } catch (requestError) {
            console.error(requestError);
            showError(root.dataset.errorGeneral);
        } finally {
            setTranslateBusy(false);
        }
    });

    explainButton.addEventListener("click", async () => {
        if (!lastTranslation) {
            return;
        }
        const request = lastTranslation;
        hideError();
        explainButton.disabled = true;
        explainLabel.textContent = root.dataset.explaining;
        try {
            const payload = await postJson("/api/student/translator/explain", request);
            // Пользователь мог изменить исходник, пока LLM готовил объяснение.
            if (lastTranslation !== request) {
                return;
            }
            renderExplanation(payload.text);
            explanation.hidden = false;
            explanation.scrollIntoView({ behavior: "smooth", block: "nearest" });
        } catch (requestError) {
            console.error(requestError);
            if (lastTranslation === request) {
                showError(root.dataset.errorGeneral);
            }
        } finally {
            explainButton.disabled = !lastTranslation;
            explainLabel.textContent = idleExplainLabel;
        }
    });

    autoGrow(source);
})();
