if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/service-worker.js').catch(() => {
      // PWA support is opportunistic and must not block server-rendered MVP pages.
    });
  });
}
