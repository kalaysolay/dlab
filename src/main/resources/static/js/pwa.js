/**
 * PWA: service worker, install UI (только мобильные), Web Push (если задан VAPID).
 */

if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/service-worker.js')
            .then(reg => { window.__swRegistration = reg; })
            .catch(() => {});
    });
}

let _installPrompt = null;
let _bannerControlsBound = false;
let _pushPromptControlsBound = false;

const PUSH_PROMPT_DISMISSED_KEY = 'pwa-push-prompt-dismissed';

function isStandalone() {
    return window.matchMedia('(display-mode: standalone)').matches
        || window.matchMedia('(display-mode: fullscreen)').matches
        || navigator.standalone === true;
}

/** Только телефоны/планшеты по UA — не десктоп, даже с touch-экраном. */
function isMobileInstallTarget() {
    return /Android|iPhone|iPad|iPod/i.test(navigator.userAgent || '');
}

function isIos() {
    return /iPhone|iPad|iPod/i.test(navigator.userAgent || '');
}

function wasBannerDismissed() {
    try {
        return sessionStorage.getItem('pwa-banner-dismissed') === '1';
    } catch {
        return false;
    }
}

function wasPushPromptDismissed() {
    try {
        return localStorage.getItem(PUSH_PROMPT_DISMISSED_KEY) === '1';
    } catch {
        return false;
    }
}

function rememberPushPromptDismissed() {
    try {
        localStorage.setItem(PUSH_PROMPT_DISMISSED_KEY, '1');
    } catch {
        /* private mode */
    }
}

function getBanner() {
    return document.getElementById('pwa-install-banner');
}

function getPushPrompt() {
    return document.getElementById('pwa-push-prompt');
}

/**
 * Баннер должен быть прямым потомком body: иначе overflow:hidden у .app-shell
 * и backdrop-filter у шапки ломают position:fixed и перехват кликов.
 */
function ensureBannerMounted() {
    const banner = getBanner();
    if (!banner) return null;
    if (banner.parentElement !== document.body) {
        document.body.appendChild(banner);
    }
    return banner;
}

function hideInstallBanner() {
    const banner = getBanner();
    if (!banner) return;
    banner.setAttribute('hidden', '');
    banner.classList.remove('is-visible');
}

function bindInstallBannerControls() {
    if (_bannerControlsBound) return;
    const banner = ensureBannerMounted();
    if (!banner) return;

    const dismissBtn = document.getElementById('pwa-install-dismiss');
    const installBtn = document.getElementById('pwa-install-btn');

    if (dismissBtn) {
        dismissBtn.addEventListener('click', e => {
            e.preventDefault();
            e.stopPropagation();
            hideInstallBanner();
            try { sessionStorage.setItem('pwa-banner-dismissed', '1'); } catch { /* private mode */ }
        });
    }

    if (installBtn) {
        installBtn.addEventListener('click', e => {
            e.preventDefault();
            e.stopPropagation();

            if (!_installPrompt) {
                notifyInstallIssue(isIos()
                    ? 'Safari: «Поделиться» → «На экран Домой».'
                    : 'Откройте меню браузера → «Установить приложение».');
                return;
            }

            try {
                _installPrompt.prompt();
            } catch {
                notifyInstallIssue('Не удалось открыть установку. Попробуйте через меню браузера.');
                return;
            }

            _installPrompt.userChoice.then(({ outcome }) => {
                if (outcome === 'accepted') {
                    _installPrompt = null;
                    hideInstallBanner();
                }
            }).catch(() => {});
        });
    }

    _bannerControlsBound = true;
}

function showInstallBanner(mode) {
    if (!isMobileInstallTarget() || isStandalone() || wasBannerDismissed()) return;

    bindInstallBannerControls();
    const banner = ensureBannerMounted();
    if (!banner) return;

    const textEl = document.getElementById('pwa-install-banner-text');
    const installBtn = document.getElementById('pwa-install-btn');

    if (mode === 'ios') {
        banner.dataset.mode = 'ios';
        if (textEl) {
            textEl.textContent = 'Safari: «Поделиться» ↗ → «На экран Домой»';
        }
        if (installBtn) installBtn.setAttribute('hidden', '');
    } else {
        banner.dataset.mode = 'android';
        if (textEl) {
            textEl.textContent = 'Установите Damulab — быстрый доступ с главного экрана';
        }
        if (installBtn) installBtn.removeAttribute('hidden');
    }

    banner.removeAttribute('hidden');
    banner.classList.add('is-visible');
}

window.addEventListener('beforeinstallprompt', e => {
    e.preventDefault();
    if (!isMobileInstallTarget() || isStandalone()) return;
    _installPrompt = e;
    showInstallBanner('android');
});

window.addEventListener('appinstalled', () => {
    _installPrompt = null;
    hideInstallBanner();
    maybeShowPushPrompt();
});

function maybeShowIosInstallHint() {
    if (!isMobileInstallTarget() || !isIos() || isStandalone() || wasBannerDismissed()) return;
    if (_installPrompt) return;
    showInstallBanner('ios');
}

function notifyInstallIssue(message) {
    if (window.DamulabUi && typeof window.DamulabUi.showToast === 'function') {
        window.DamulabUi.showToast({ kind: 'warning', title: 'Установка приложения', body: message });
        return;
    }
    const textEl = document.getElementById('pwa-install-banner-text');
    if (textEl) textEl.textContent = message;
}

function ensurePushPromptMounted() {
    let prompt = getPushPrompt();
    if (prompt) return prompt;

    prompt = document.createElement('div');
    prompt.id = 'pwa-push-prompt';
    prompt.className = 'pwa-push-prompt';
    prompt.hidden = true;
    prompt.setAttribute('aria-live', 'polite');
    prompt.innerHTML = [
        '<div class="pwa-push-prompt-inner">',
        '  <div class="pwa-push-prompt-copy">',
        '    <strong>Включить push-уведомления?</strong>',
        '    <span>Подскажем о новых тестах, викторинах и важных заданиях.</span>',
        '  </div>',
        '  <button id="pwa-push-prompt-enable" class="button primary pwa-push-prompt-enable" type="button">Включить</button>',
        '  <button id="pwa-push-prompt-dismiss" class="pwa-push-prompt-dismiss" type="button" aria-label="Закрыть">×</button>',
        '</div>'
    ].join('');
    document.body.appendChild(prompt);
    return prompt;
}

function hidePushPrompt() {
    const prompt = getPushPrompt();
    if (!prompt) return;
    prompt.setAttribute('hidden', '');
    prompt.classList.remove('is-visible');
}

function bindPushPromptControls() {
    if (_pushPromptControlsBound) return;
    ensurePushPromptMounted();
    const enableBtn = document.getElementById('pwa-push-prompt-enable');
    const dismissBtn = document.getElementById('pwa-push-prompt-dismiss');

    if (enableBtn) {
        enableBtn.addEventListener('click', async e => {
            e.preventDefault();
            e.stopPropagation();
            enableBtn.disabled = true;
            try {
                const subscribed = await subscribeToPush();
                if (subscribed) {
                    hidePushPrompt();
                    rememberPushPromptDismissed();
                    const profileBtn = document.getElementById('pwa-push-subscribe-btn');
                    if (profileBtn) setPushBtnSubscribed(profileBtn);
                    if (window.DamulabUi && typeof window.DamulabUi.showToast === 'function') {
                        window.DamulabUi.showToast({
                            kind: 'success',
                            title: 'Уведомления включены',
                            body: 'Теперь Damulab сможет отправлять push на это устройство.'
                        });
                    }
                    return;
                }
                enableBtn.disabled = false;
            } catch {
                enableBtn.disabled = false;
                if (window.DamulabUi && typeof window.DamulabUi.showToast === 'function') {
                    window.DamulabUi.showToast({
                        kind: 'warning',
                        title: 'Push не включился',
                        body: 'Проверьте разрешения браузера и попробуйте еще раз.'
                    });
                }
            }
        });
    }

    if (dismissBtn) {
        dismissBtn.addEventListener('click', e => {
            e.preventDefault();
            e.stopPropagation();
            rememberPushPromptDismissed();
            hidePushPrompt();
        });
    }

    _pushPromptControlsBound = true;
}

async function shouldShowPushPrompt() {
    if (!document.body || !document.querySelector('.student-header')) return false;
    if (wasPushPromptDismissed()) return false;

    const vapidMeta = document.querySelector('meta[name="vapid-public-key"]');
    if (!vapidMeta || !vapidMeta.content) return false;
    if (!('serviceWorker' in navigator) || !('Notification' in window) || !('PushManager' in window)) return false;
    if (Notification.permission === 'denied') return false;

    const reg = window.__swRegistration ?? await navigator.serviceWorker.ready;
    if (!reg) return false;
    const existing = await reg.pushManager.getSubscription();
    return !existing;
}

async function maybeShowPushPrompt() {
    try {
        if (!(await shouldShowPushPrompt())) return;
        bindPushPromptControls();
        const prompt = ensurePushPromptMounted();
        window.setTimeout(() => {
            if (wasPushPromptDismissed()) return;
            const installBanner = getBanner();
            if (installBanner && installBanner.classList.contains('is-visible')) return;
            prompt.removeAttribute('hidden');
            requestAnimationFrame(() => prompt.classList.add('is-visible'));
        }, 1200);
    } catch {
        /* Push prompt is best-effort UI. */
    }
}

function initPwaUi() {
    ensureBannerMounted();
    bindInstallBannerControls();
    maybeShowIosInstallHint();
    maybeShowPushPrompt();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initPwaUi);
} else {
    initPwaUi();
}

document.addEventListener('DOMContentLoaded', () => {
    const pushBtn = document.getElementById('pwa-push-subscribe-btn');
    if (!pushBtn) return;
    checkPushStatus(pushBtn);
    pushBtn.addEventListener('click', async () => {
        pushBtn.disabled = true;
        try {
            const subscribed = await subscribeToPush();
            if (subscribed) {
                rememberPushPromptDismissed();
                hidePushPrompt();
                setPushBtnSubscribed(pushBtn);
            } else {
                pushBtn.disabled = false;
            }
        } catch {
            pushBtn.disabled = false;
        }
    });
});

async function subscribeToPush() {
    const vapidMeta = document.querySelector('meta[name="vapid-public-key"]');
    if (!vapidMeta || !vapidMeta.content) return false;
    if (!('Notification' in window) || !('PushManager' in window)) return false;

    const reg = window.__swRegistration ?? await navigator.serviceWorker.ready;
    if (!reg) return false;

    const existing = await reg.pushManager.getSubscription();
    if (existing) {
        await savePushSubscription(existing);
        return true;
    }

    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return false;

    const subscription = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidMeta.content)
    });

    await savePushSubscription(subscription);
    return true;
}

async function savePushSubscription(subscription) {
    // Добавляем тайм-зону устройства — используется сервером для персонализированного расписания
    const subscriptionData = {
        ...subscription.toJSON(),
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || null
    };

    const response = await fetch('/api/push/subscribe', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(subscriptionData)
    });
    if (!response.ok) {
        throw new Error('Push subscription was not saved');
    }
}

async function checkPushStatus(btn) {
    if (!('Notification' in window) || !('PushManager' in window)) {
        btn.disabled = true;
        btn.textContent = 'Push не поддерживается';
        return;
    }
    if (Notification.permission === 'granted') {
        const reg = window.__swRegistration ?? await navigator.serviceWorker.ready;
        const existing = reg ? await reg.pushManager.getSubscription() : null;
        if (existing) setPushBtnSubscribed(btn);
    } else if (Notification.permission === 'denied') {
        btn.disabled = true;
        btn.textContent = 'Уведомления заблокированы в браузере';
    }
}

function setPushBtnSubscribed(btn) {
    btn.disabled = true;
    btn.textContent = 'Уведомления включены ✓';
    btn.classList.add('is-subscribed');
}

function urlBase64ToUint8Array(base64String) {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
    const rawData = window.atob(base64);
    return Uint8Array.from([...rawData].map(c => c.charCodeAt(0)));
}
