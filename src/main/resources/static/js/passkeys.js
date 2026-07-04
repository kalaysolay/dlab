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
            throw new Error(await response.text());
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
        setStatus(status, "Откройте системное окно и подтвердите вход на устройстве.", false);
        button.disabled = true;
        try {
            const options = await fetchJson("/api/passkeys/register/options", { method: "POST" });
            const credential = await navigator.credentials.create(prepareCreateOptions(options));
            await fetchJson("/api/passkeys/register", {
                method: "POST",
                body: JSON.stringify(encodeAttestationCredential(credential))
            });
            setStatus(status, "Устройство привязано. Теперь можно входить по отпечатку/Passkey.", false);
        } catch (error) {
            setStatus(status, "Не удалось привязать устройство. Попробуйте еще раз.", true);
        } finally {
            button.disabled = false;
        }
    }

    async function loginWithPasskey(button, status) {
        const usernameInput = document.getElementById("username");
        const username = usernameInput ? usernameInput.value.trim() : "";
        if (!username) {
            setStatus(status, "Введите email, затем повторите вход по Passkey.", true);
            usernameInput?.focus();
            return;
        }
        setStatus(status, "Подтвердите вход на устройстве.", false);
        button.disabled = true;
        try {
            const options = await fetchJson("/api/passkeys/login/options", {
                method: "POST",
                body: JSON.stringify({ username })
            });
            const credential = await navigator.credentials.get(prepareGetOptions(options));
            const result = await fetchJson("/api/passkeys/login", {
                method: "POST",
                body: JSON.stringify(encodeAssertionCredential(credential))
            });
            window.location.assign(result.redirectUrl || "/dashboard");
        } catch (error) {
            setStatus(status, "Вход по Passkey не удался. Можно войти по паролю.", true);
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
    });
})();
