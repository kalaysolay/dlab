/** Запускается после разметки /app, не зависит от DOMContentLoaded или версии общего скрипта в кэше. */
(function () {
    const entry = document.querySelector('[data-pwa-entry-home]');
    if (!entry) return;
    const home = entry.dataset.pwaEntryHome;
    let target = home;
    try {
        const url = new URL(window.damulabPwaEntryTarget ? window.damulabPwaEntryTarget(home) : home, window.location.origin);
        if (url.origin === window.location.origin && (url.pathname === home || url.pathname.startsWith(home + '/'))) {
            target = url.pathname + url.search + url.hash;
        }
    } catch (ignored) { /* Старая версия общего JS не должна оставлять человека на переходном экране. */ }
    const link = entry.querySelector('a');
    if (link) link.href = target;
    window.location.replace(target);
})();
