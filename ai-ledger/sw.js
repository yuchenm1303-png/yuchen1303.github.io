const CACHE_NAME = 'ai-ledger-v25';
const ASSETS = [
  './',
  './index.html',
  './styles.css',
  './liquid.css',
  './chat-v2.css',
  './auth.css',
  './liquid-plus.css',
  './modal-fix.css',
  './backgrounds.css',
  './config.js',
  './app-v3.js',
  './chat-actions.js',
  './assistant-profile.js',
  './navigation-preferences.js',
  './chat-attachments.js',
  './chat-source-badges.js',
  './tools-center.js',
  './ai-command-router-v2.js',
  './cloud-command-bridge.js',
  './ui-motion.js',
  './background-picker.js',
  './settings-appearance-plus.js',
  './settings-preferences.js',
  './glass-stability.js',
  './settings-performance-mode.js',
  './settings-groups.js',
  './settings-detail-polish.js',
  './ui-density-polish.js',
  './ios-glass-motion.js',
  './layout-stability-polish.js',
  './navigation-polish.js',
  './navigation-execution-compat.js',
  './chat-request-patcher.js',
  './chat-scroll-stability.js',
  './chat-typing-indicator.js',
  './chat-card-polish.js',
  './chat-message-actions-polish.js',
  './chat-source-badges-core.js',
  './chat-badge-actions-hardener.js',
  './chat-model-picker.js',
  './settings-performance-polish.js',
  './settings-preferences-sync.js',
  './auth.js',
  './sync.js',
  './manifest.webmanifest',
  './icon.svg',
  './icon-192.png',
  './icon-512.png',
  './liquid-bg.svg',
  './bg-jade-ocean.svg',
  './bg-sunset-glow.svg',
  './bg-dawn-pearl.svg'
];

self.addEventListener('install', (event) => {
  self.skipWaiting();
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS)));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put('./index.html', copy));
          return response;
        })
        .catch(() => caches.match('./index.html'))
    );
    return;
  }

  event.respondWith(caches.match(request).then((response) => response || fetch(request)));
});
