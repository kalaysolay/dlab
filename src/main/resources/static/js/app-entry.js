/**
 * Старые установки могут сохранять start_url=/. Направляем их на экран биометрии до рендера.
 * При возврате в установленное приложение также закрываем кабинет экраном входа.
 * Это UI-маршрутизация; флаг localStorage не заменяет серверную проверку WebAuthn.
 */
(function () {
    const standalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
    if (!standalone) return;
    if (window.location.pathname === '/') {
        window.location.replace('/app');
        return;
    }
    // Возврат из системной биометрии, OAuth и регистрации не должен прерывать эти процессы.
    const privatePage = /^\/(student|parent|admin|dashboard)(\/|$)/.test(window.location.pathname);
    if (!privatePage) return;
    let needsEntry = false;
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'hidden' && !window.damulabBiometricPromptActive) {
            try { needsEntry = localStorage.getItem('damulab-biometric-enabled') === '1'; } catch (ignored) {}
            if (needsEntry) document.documentElement.classList.add('biometric-resume');
        } else if (document.visibilityState === 'visible' && needsEntry) {
            window.location.replace('/app');
        }
    });
    // Back/forward cache тоже может восстановить старый кабинет без нового запроса к серверу.
    window.addEventListener('pageshow', event => {
        if (event.persisted && needsEntry) window.location.replace('/app');
    });
})();
