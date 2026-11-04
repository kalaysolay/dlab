/** PWA сохраняет текущий документ при возврате из фона; сервер по-прежнему проверяет вход и права. */
(function () {
    const standalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
    const key = 'damulab-pwa-last-route';
    const privatePath = /^\/(student|parent|admin)(\/|$)/;
    let leavingAccount = false;

    // Сохраняем только маршрут, язык и известные состояния разговорника, без текстов, фильтров и токенов.
    function safeRoute(value) {
        try {
            if (typeof value !== 'string' || value.length > 2048 || !value.startsWith('/') || value.startsWith('//')) return null;
            const url = new URL(value, window.location.origin);
            if (url.origin !== window.location.origin || !privatePath.test(url.pathname)) return null;
            const lang = url.searchParams.get('lang');
            const query = lang === 'ru' || lang === 'kk' ? '?lang=' + lang : '';
            const hash = url.pathname === '/student/speaking' && /^#(?:session\/[a-f0-9-]{36}|setup\/[a-z_]+|catalog|history)$/.test(url.hash) ? url.hash : '';
            return url.pathname + query + hash;
        } catch (ignored) { return null; }
    }
    function rememberRoute() {
        if (leavingAccount) return;
        const route = safeRoute(window.location.pathname + window.location.search + window.location.hash);
        if (route) try { sessionStorage.setItem(key, route); } catch (ignored) { /* storage может быть выключен */ }
    }
    // Используется и после WebAuthn: новый аккаунт другой роли не получает маршрут предыдущего кабинета.
    window.damulabPwaEntryTarget = function (home) {
        if (!standalone) return home;
        try {
            const route = safeRoute(sessionStorage.getItem(key));
            if (route && (route.split(/[?#]/)[0] === home || route.startsWith(home + '/'))) return route;
        } catch (ignored) { /* без storage открываем обычный кабинет */ }
        return home;
    };
    if (!standalone) return;
    if (window.location.pathname === '/') {
        window.location.replace('/app');
        return;
    }
    rememberRoute();
    document.addEventListener('submit', event => {
        const form = event.target;
        if (form.action && new URL(form.action, window.location.origin).pathname === '/logout') {
            // Следующий член семьи начинает с собственного кабинета; pagehide не записывает старый адрес повторно.
            leavingAccount = true;
            try { sessionStorage.removeItem(key); } catch (ignored) { /* storage может быть выключен */ }
        }
    });
    window.addEventListener('hashchange', rememberRoute);
    window.addEventListener('pagehide', rememberRoute);
    // Не навигируем при visibilitychange/pageshow: остаются DOM, прокрутка и неотправленный ввод.
    // focus-existing передаёт сюда явные deep links; запуск с иконки (/app или /) оставляет экран как есть.
    if (window.launchQueue && typeof window.launchQueue.setConsumer === 'function') {
        window.launchQueue.setConsumer(params => {
            if (!params.targetURL) return;
            try {
                const url = new URL(params.targetURL, window.location.origin);
                if (url.origin !== window.location.origin || url.pathname === '/' || url.pathname === '/app') return;
                // Явная ссылка сохраняет свои параметры; в sessionStorage они всё равно минимизируются.
                const target = url.pathname + url.search + url.hash;
                if (target !== window.location.pathname + window.location.search + window.location.hash) window.location.assign(target);
            } catch (ignored) { /* некорректный launch URL не меняет текущий документ */ }
        });
    }
})();
