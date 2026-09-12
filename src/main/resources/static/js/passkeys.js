(function () {
    const unsupportedMessage = "Passkey недоступен в этом браузере или контексте. Нужен HTTPS или localhost.";

    function isSupported() {
        return Boolean(window.PublicKeyCredential && navigator.credentials);
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
        if (!response.ok) {
            const text = await response.text();
            let message = text;
            try {
                message = JSON.parse(text).message || text;
            } catch (ignored) {
                // Не-JSON ответ (например, от reverse proxy) всё равно попадёт в console для диагностики.
            }
            const error = new Error(message || `HTTP ${response.status}`);
            error.status = response.status;
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
            button.textContent = "Добавить другое устройство";
            setStatus(status, "Готово. Теперь на этом устройстве можно входить по отпечатку.", false);
        } catch (error) {
            console.error("Passkey registration failed", error);
            if (error.name === "NotAllowedError") {
                setStatus(status, "Настройка отменена или системное окно закрылось. Нажмите кнопку, чтобы повторить.", true);
            } else if (error.name === "InvalidStateError") {
                setStatus(status, "Это устройство уже настроено для входа. Попробуйте войти по отпечатку.", true);
            } else if (error.status === 401 || error.status === 403) {
                setStatus(status, "Сессия истекла. Войдите снова и повторите настройку.", true);
            } else if (error instanceof TypeError) {
                setStatus(status, "Нет связи с сервером. Проверьте интернет и повторите.", true);
            } else {
                setStatus(status, "Сервер не принял настройку. Ошибка записана в журнал; обновите страницу и повторите.", true);
            }
        } finally {
            button.disabled = false;
        }
    }

    async function loginWithPasskey(button, status) {
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
            window.location.assign(result.redirectUrl || "/dashboard");
        } catch (error) {
            console.error("Passkey login failed", error);
            const message = error.name === "NotAllowedError"
                ? "Вход отменён. Можно повторить или войти по паролю."
                : "Не удалось войти по отпечатку. Проверьте email или войдите по паролю.";
            setStatus(status, message, true);
        } finally {
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
            // Параметр одноразовый: reload после успешной настройки не должен снова открывать биометрию.
            query.delete("passkeySetup");
            const cleanQuery = query.toString();
            const cleanUrl = window.location.pathname
                + (cleanQuery ? `?${cleanQuery}` : "")
                + window.location.hash;
            window.history.replaceState(null, "", cleanUrl);
            window.setTimeout(() => registerPasskey(registerButton, registerStatus), 350);
        }
    });
})();
