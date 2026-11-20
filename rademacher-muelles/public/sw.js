/* Service worker: recibe los avisos push (turno, cambio de muelle, mensajes de oficina)
 * y los muestra como notificación, también con la app cerrada o la pantalla bloqueada.
 * No guarda nada en caché: la información de turnos siempre se pide al servidor. */
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));

self.addEventListener('push', (e) => {
  let d = {};
  try { d = e.data ? e.data.json() : {}; } catch { d = { body: e.data && e.data.text() }; }
  e.waitUntil(self.registration.showNotification(d.title || 'Rademacher', {
    body: d.body || '',
    tag: d.tag || 'turno',
    renotify: true,
    requireInteraction: true,
    icon: '/icon-192.png',
    badge: '/icon-192.png',
    vibrate: [400, 150, 400, 150, 400],
    data: { url: d.url || '/chofer' },
  }));
});

self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const url = (e.notification.data && e.notification.data.url) || '/chofer';
  e.waitUntil((async () => {
    const all = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const c of all) {
      if (new URL(c.url).pathname === url && 'focus' in c) return c.focus();
    }
    return self.clients.openWindow(url);
  })());
});
