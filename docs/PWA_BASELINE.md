# PWA Baseline

Status: minimal checkable baseline.

## Scope

The MVP is still server-rendered Spring MVC + Thymeleaf. The PWA layer is limited to installability/offline shell assets and does not introduce a separate SPA.

## Files

- `/manifest.webmanifest`
- `/service-worker.js`
- `/js/pwa.js`
- `/icons/damulab-icon.svg`
- `fragments/pwa-head.html`

## Current Behavior

- Main pages link the web manifest.
- Header fragment loads `pwa.js`.
- `pwa.js` registers `/service-worker.js` when the browser supports Service Worker.
- Service worker caches the public shell: `/`, `/login`, CSS, manifest, and icon.
- Authenticated pages are still server-rendered and require normal Spring Security session access.

## Automated Checks

`PwaSmokeTest` verifies:

- The home page links `/manifest.webmanifest` and `/js/pwa.js`.
- The manifest is publicly available and has `display: standalone`.
- The service worker is publicly available and contains cache/fetch handlers.

## Manual Browser Check

Full PWA verification still requires a browser run on localhost:

- Open `/`.
- Check that the manifest is detected.
- Check that service worker registration succeeds.
- Reload after switching the browser offline and verify the cached public shell still opens.
