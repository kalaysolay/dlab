/**
 * Универсальные in-app уведомления (стек в правом верхнем углу).
 * Не заменяет модуль admin push / device tokens — только DOM.
 */
(function (global) {
    const DEFAULT_TTL_MS = 7200;

    /**
     * @param {object} options
     * @param {'info'|'success'|'warning'|'error'} [options.kind]
     * @param {string} options.title
     * @param {string} [options.body]
     * @param {number} [options.ttlMs]
     */
    function showToast(options) {
        if (!options || typeof options !== "object") {
            return;
        }
        const root = document.getElementById("toast-stack");
        if (!root) {
            return;
        }
        const kind = options.kind || "info";
        const title = options.title || "";
        const body = options.body || "";
        const ttlMs = typeof options.ttlMs === "number" ? options.ttlMs : DEFAULT_TTL_MS;

        const node = document.createElement("div");
        node.className = "toast-card toast-" + kind;
        node.setAttribute("role", "status");

        const t = document.createElement("strong");
        t.className = "toast-title";
        t.textContent = title;
        node.appendChild(t);

        if (body) {
            const p = document.createElement("p");
            p.className = "toast-body";
            p.textContent = body;
            node.appendChild(p);
        }

        const close = document.createElement("button");
        close.type = "button";
        close.className = "toast-close";
        close.setAttribute("aria-label", "Закрыть");
        close.textContent = "×";
        close.addEventListener("click", () => dismiss(node));
        node.appendChild(close);

        root.appendChild(node);
        requestAnimationFrame(() => node.classList.add("is-visible"));

        const timer = window.setTimeout(() => dismiss(node), ttlMs);
        close.addEventListener(
            "click",
            () => {
                window.clearTimeout(timer);
            },
            { once: true }
        );
    }

    function dismiss(node) {
        if (!node || !node.parentNode) {
            return;
        }
        node.classList.remove("is-visible");
        node.addEventListener(
            "transitionend",
            () => {
                node.remove();
            },
            { once: true }
        );
    }

    global.DamulabUi = global.DamulabUi || {};
    global.DamulabUi.showToast = showToast;
})(typeof window !== "undefined" ? window : globalThis);
