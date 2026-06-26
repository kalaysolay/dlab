/**
 * Damulab Service Worker v2.
 *
 * Стратегия: network-first с записью в кэш; при офлайне — кэш, затем /offline.
 * SHELL_ASSETS предзагружаются при install: критичные страницы + статика.
 * Push-события: showNotification + notificationclick с открытием целевого URL.
 *
 * Версия кэша: меняй CACHE_NAME при обновлении shell-ресурсов, чтобы старый кэш очистился.
 */
const CACHE_NAME = 'damulab-shell-v2';

// Ресурсы публичной оболочки, кэшируемые при первом install.
// /offline — обязателен: используется как fallback при отсутствии сети.
const SHELL_ASSETS = [
    '/',
    '/login',
    '/offline',
    '/css/app.css',
    '/css/fonts.css',
    '/icons/damulab-icon.svg',
    '/icons/icon-192.png',
    '/icons/icon-512.png',
    '/manifest.webmanifest',
    '/fonts/manrope/Manrope-wght.ttf',
    '/fonts/nunito/Nunito-wght.ttf'
];

// --- Install: precache shell ---

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => cache.addAll(SHELL_ASSETS))
            .then(() => self.skipWaiting())
    );
});

// --- Activate: чистим старые версии кэша ---

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(
                keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
            ))
            .then(() => self.clients.claim())
    );
});

// --- Fetch: network-first, офлайн-fallback ---

self.addEventListener('fetch', event => {
    if (event.request.method !== 'GET') return;

    // Навигационные запросы (HTML-страницы): офлайн → /offline
    const isNavigation = event.request.mode === 'navigate';

    event.respondWith(
        fetch(event.request)
            .then(response => {
                // Кэшируем успешные ответы (не 4xx/5xx)
                if (response.ok) {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(event.request, copy));
                }
                return response;
            })
            .catch(() =>
                caches.match(event.request).then(cached => {
                    if (cached) return cached;
                    // Нет сети и нет в кэше: для навигации — offline-страница
                    if (isNavigation) return caches.match('/offline');
                    return new Response('', { status: 503 });
                })
            )
    );
});

// --- Push: получение серверного уведомления ---

self.addEventListener('push', event => {
    // Данные приходят в формате JSON: {title, body, url}
    let data = {};
    try {
        data = event.data ? event.data.json() : {};
    } catch {
        data = { body: event.data ? event.data.text() : '' };
    }

    const title = data.title ?? 'Damulab';
    const options = {
        body: data.body ?? '',
        icon: '/icons/icon-192.png',
        badge: '/icons/icon-192.png',
        data: { url: data.url ?? '/' },
        requireInteraction: false
    };

    event.waitUntil(self.registration.showNotification(title, options));
});

// --- Notification click: открываем целевой URL ---

self.addEventListener('notificationclick', event => {
    event.notification.close();
    const targetUrl = event.notification.data?.url ?? '/';

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true })
            .then(windowClients => {
                // Если уже открыта вкладка с этим URL — фокусируем её
                const existing = windowClients.find(c => c.url === targetUrl);
                if (existing) return existing.focus();
                return clients.openWindow(targetUrl);
            })
    );
});
