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

function getBanner() {
    return document.getElementById('pwa-install-banner');
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
    subscribeToPush().catch(() => {});
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

function initPwaUi() {
    ensureBannerMounted();
    bindInstallBannerControls();
    maybeShowIosInstallHint();
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
            await subscribeToPush();
            setPushBtnSubscribed(pushBtn);
        } catch {
            pushBtn.disabled = false;
        }
    });
});

async function subscribeToPush() {
    const vapidMeta = document.querySelector('meta[name="vapid-public-key"]');
    if (!vapidMeta || !vapidMeta.content) return;
    if (!('Notification' in window) || !('PushManager' in window)) return;

    const reg = window.__swRegistration ?? await navigator.serviceWorker.ready;
    if (!reg) return;

    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return;

    const subscription = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidMeta.content)
    });

    // Добавляем тайм-зону устройства — используется сервером для персонализированного расписания
    const subscriptionData = {
        ...subscription.toJSON(),
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || null
    };

    await fetch('/api/push/subscribe', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(subscriptionData)
    });
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
