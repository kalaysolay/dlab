(function () {
    const unsupportedMessage = "Биометрия недоступна в этом браузере. Откройте приложение в Chrome или Safari по HTTPS либо войдите по паролю.";
    const enabledKey = "damulab-biometric-enabled";
    let busy = false;

    // Это только предпочтение интерфейса, не подтверждение входа. Подпись всегда проверяет сервер.
    function rememberEnabled() {
        try { localStorage.setItem(enabledKey, "1"); } catch (ignored) { /* private mode */ }
    }

    function wasEnabled() {
        try { return localStorage.getItem(enabledKey) === "1"; } catch (ignored) { return false; }
    }

    function errorMessage(error) {
        if (error.name === "NotAllowedError" || error.name === "AbortError") {
            return "Подтверждение отменено или время ожидания истекло. Повторите попытку либо войдите по паролю.";
        }
        if (error.name === "InvalidStateError") {
            return "Ключ уже сохранён. Нажмите «Использовать сохранённый ключ» или войдите по биометрии на экране входа.";
        }
        if (error.status === 401 || error.status === 403) {
            return "Сессия истекла. Войдите по паролю и повторите настройку.";
        }
        if (error.name === "SecurityError") {
            return "Не удалось открыть биометрию для этого адреса. Откройте приложение по основному адресу Damulab через HTTPS.";
        }
        if (error instanceof TypeError) {
            return "Нет связи с сервером. Проверьте интернет и повторите.";
        }
        return error.userMessage || "Не удалось подтвердить ключ доступа. Повторите попытку или войдите по паролю.";
    }

    function isSupported() {
        return Boolean(window.isSecureContext && window.PublicKeyCredential && navigator.credentials);
    }

    function base64UrlToBuffer(value) {
        const padding = "=".repeat((4 - value.length % 4) % 4);
        const base64 = (value + padding).replace(/-/g, "+").replace(/_/g, "/");
        const binary = window.atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i += 1) {
            bytes[i] = binary.charCodeAt(i);
        }
        return bytes.buffer;
    }

    function bufferToBase64Url(buffer) {
        const bytes = new Uint8Array(buffer);
        let binary = "";
        for (const byte of bytes) {
            binary += String.fromCharCode(byte);
        }
        return window.btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
    }

    function prepareCreateOptions(options) {
        const publicKey = options.publicKey || options;
        publicKey.challenge = base64UrlToBuffer(publicKey.challenge);
        publicKey.user.id = base64UrlToBuffer(publicKey.user.id);
        if (publicKey.excludeCredentials) {
            publicKey.excludeCredentials = publicKey.excludeCredentials.map((credential) => ({
                ...credential,
                id: base64UrlToBuffer(credential.id)
            }));
        }
        return options.publicKey ? options : { publicKey };
    }

    function prepareGetOptions(options) {
        const publicKey = options.publicKey || options;
        publicKey.challenge = base64UrlToBuffer(publicKey.challenge);
        if (publicKey.allowCredentials) {
            publicKey.allowCredentials = publicKey.allowCredentials.map((credential) => ({
                ...credential,
                id: base64UrlToBuffer(credential.id)
            }));
        }
        return options.publicKey ? options : { publicKey };
    }

    function encodeAttestationCredential(credential) {
        return {
            id: credential.id,
            rawId: bufferToBase64Url(credential.rawId),
            type: credential.type,
            response: {
                attestationObject: bufferToBase64Url(credential.response.attestationObject),
                clientDataJSON: bufferToBase64Url(credential.response.clientDataJSON),
                transports: typeof credential.response.getTransports === "function"
                    ? credential.response.getTransports()
                    : []
            },
            clientExtensionResults: credential.getClientExtensionResults()
        };
    }

    function encodeAssertionCredential(credential) {
        return {
            id: credential.id,
            rawId: bufferToBase64Url(credential.rawId),
            type: credential.type,
            response: {
                authenticatorData: bufferToBase64Url(credential.response.authenticatorData),
                clientDataJSON: bufferToBase64Url(credential.response.clientDataJSON),
                signature: bufferToBase64Url(credential.response.signature),
                userHandle: credential.response.userHandle
                    ? bufferToBase64Url(credential.response.userHandle)
                    : null
            },
            clientExtensionResults: credential.getClientExtensionResults()
        };
    }

    async function fetchJson(url, options) {
        const response = await fetch(url, {
            credentials: "same-origin",
            headers: { "Content-Type": "application/json" },
            ...options
        });
        if (!response.ok || response.redirected) {
            let data = {};
            try { data = await response.json(); } catch (ignored) { /* proxy/HTML error */ }
            const error = new Error(data.error || "Request failed");
            error.status = response.redirected ? 401 : response.status;
            // Только известный JSON-контракт: HTML reverse proxy и stack trace пользователю не показываем.
            if (data.message && data.reference) {
                error.userMessage = data.message + " Если ошибка повторяется, сообщите поддержке код: " + data.reference;
            }
            throw error;
        }
        return response.json();
    }

    function setStatus(element, text, isError) {
        if (!element) {
            return;
        }
        element.textContent = text;
        element.classList.toggle("error", Boolean(isError));
    }

    async function registerPasskey(button, status) {
        if (busy) return;
        busy = true;
        window.damulabBiometricPromptActive = true;
        setStatus(status, "Подтвердите отпечаток, распознавание лица или PIN устройства.", false);
        button.disabled = true;
        try {
            const options = await fetchJson("/api/passkeys/register/options", { method: "POST" });
            const credential = await navigator.credentials.create(prepareCreateOptions(options));
            if (!credential) {
                throw new DOMException("Credential creation returned no result", "NotAllowedError");
            }
            await fetchJson("/api/passkeys/register", {
                method: "POST",
                body: JSON.stringify(encodeAttestationCredential(credential))
            });
            rememberEnabled();
            button.textContent = "Биометрия включена";
            const continueLink = document.getElementById("passkey-setup-continue");
            if (continueLink) {
                continueLink.textContent = "Продолжить";
                continueLink.className = "button primary";
                continueLink.focus();
            }
            setStatus(status, "Готово! При открытии приложения вход будет подтверждаться биометрией или PIN устройства.", false);
        } catch (error) {
            console.error("Passkey registration failed", error);
            setStatus(status, errorMessage(error), true);
        } finally {
            busy = false;
            window.damulabBiometricPromptActive = false;
            button.disabled = false;
        }
    }

    async function loginWithPasskey(button, status) {
        if (busy) return;
        busy = true;
        window.damulabBiometricPromptActive = true;
        const usernameInput = document.getElementById("username");
        const username = usernameInput ? usernameInput.value.trim() : "";
        setStatus(status, "Подтвердите отпечаток, распознавание лица или PIN устройства.", false);
        button.disabled = true;
        try {
            const options = await fetchJson("/api/passkeys/login/options", {
                method: "POST",
                // Новый discoverable passkey сам сообщает аккаунт. Введённый email оставляем
                // как fallback для ранее созданных недискаверируемых WebAuthn-ключей.
                body: JSON.stringify({ username: username || null })
            });
            const credential = await navigator.credentials.get(prepareGetOptions(options));
            if (!credential) {
                throw new DOMException("Credential request returned no result", "NotAllowedError");
            }
            const result = await fetchJson("/api/passkeys/login", {
                method: "POST",
                body: JSON.stringify(encodeAssertionCredential(credential))
            });
            rememberEnabled();
            window.location.replace(result.redirectUrl || "/dashboard");
        } catch (error) {
            console.error("Passkey login failed", error);
            setStatus(status, errorMessage(error), true);
        } finally {
            busy = false;
            window.damulabBiometricPromptActive = false;
            button.disabled = false;
        }
    }

    document.addEventListener("DOMContentLoaded", () => {
        const registerButton = document.getElementById("passkey-register-button");
        const registerStatus = document.getElementById("passkey-register-status");
        const loginButton = document.getElementById("passkey-login-button");
        const loginStatus = document.getElementById("passkey-login-status");

        if (!isSupported()) {
            [registerButton, loginButton].forEach((button) => {
                if (button) {
                    button.disabled = true;
                    button.hidden = true;
                }
            });
            setStatus(registerStatus, unsupportedMessage, true);
            setStatus(loginStatus, unsupportedMessage, true);
            return;
        }

        registerButton?.addEventListener("click", () => registerPasskey(registerButton, registerStatus));
        loginButton?.addEventListener("click", () => loginWithPasskey(loginButton, loginStatus));
        const query = new URLSearchParams(window.location.search);
        if (registerButton && query.get("passkeySetup") === "true") {
            // Совместимость со старыми ссылками: показываем предложение, не создаём ключ без согласия.
            window.location.replace("/passkeys/setup");
            return;
        }
        if (loginButton && document.body.hasAttribute("data-passkey-entry") && wasEnabled()) {
            // Обычный modal WebAuthn-запрос, а не conditional autofill, требующий фокуса email.
            // Если браузер требует жест пользователя, остаётся видимая кнопка повторного входа.
            loginWithPasskey(loginButton, loginStatus);
        }
    });
})();
