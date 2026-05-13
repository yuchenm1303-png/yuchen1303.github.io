const CACHE_NAME = 'ai-ledger-v16';
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
  './liquid-refraction.css',
  './config.js',
  './app-v3.js',
  './chat-actions.js',
  './ui-motion.js',
  './background-picker.js',
  './liquid-refraction.js',
  './auth.js',
  './sync.js',
  './manifest.webmanifest',
  './icon.svg',
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
