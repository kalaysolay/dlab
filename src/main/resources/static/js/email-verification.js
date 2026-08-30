(() => {
    const form = document.getElementById('verification-resend-form');
    const button = document.getElementById('verification-resend-button');
    const status = document.getElementById('verification-resend-status');
    if (!form || !button || !status) return;

    const storageKey = 'damulab.emailVerification.resendAvailableAt';
    const baseLabel = button.dataset.label || button.textContent;

    const render = () => {
        const availableAt = Number(sessionStorage.getItem(storageKey) || 0);
        const seconds = Math.max(0, Math.ceil((availableAt - Date.now()) / 1000));
        button.disabled = seconds > 0;
        button.textContent = seconds > 0 ? `${baseLabel} (${seconds})` : baseLabel;
        status.textContent = seconds > 0 ? `${seconds} с` : '';
        if (seconds > 0) window.setTimeout(render, 250);
    };

    form.addEventListener('submit', () => {
        sessionStorage.setItem(storageKey, String(Date.now() + 60_000));
        render();
    });

    if (new URLSearchParams(window.location.search).has('verificationResent')
            && Number(sessionStorage.getItem(storageKey) || 0) <= Date.now()) {
        sessionStorage.setItem(storageKey, String(Date.now() + 60_000));
    }
    render();
})();
