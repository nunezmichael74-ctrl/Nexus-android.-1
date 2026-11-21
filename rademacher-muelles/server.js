'use strict';
/*
 * Rademacher SL · Gestión de muelles y turnos de chóferes
 * Servidor sin dependencias externas (Node.js >= 18).
 *
 * Flujo:
 *  1. El chófer entrega la documentación en oficina.
 *  2. Oficina lo registra y le asigna un muelle (1-14). Se genera un código + QR.
 *  3. El chófer abre su página (/c/CODIGO) y ve muelle, posición en la cola y estado,
 *     en su idioma.
 *  4. Cuando el muelle queda libre, el primero de la cola pasa a "turno" (aviso en pantalla).
 *  5. El chófer pulsa "Estoy en el muelle" y, al terminar, "Carga/descarga finalizada".
 *     Automáticamente se llama al siguiente chófer de ese muelle.
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const vm = require('vm');
const webpush = require('web-push');

const PORT = Number(process.env.PORT || 3000);
const DOCKS = Number(process.env.DOCKS || 14);
const OFFICE_PIN = String(process.env.OFFICE_PIN || '1234');
const RETENTION_DAYS = Number(process.env.RETENTION_DAYS || 7);
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, 'data', 'state.json');
const PUBLIC_DIR = path.join(__dirname, 'public');

const ACTIVE = ['espera', 'turno', 'en_muelle'];
const MESSAGES = ['m_office', 'm_docs', 'm_wait', 'm_doors', 'm_safety'];
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

// ---------------------------------------------------------------- notificaciones push (iPhone / Android)
// Claves VAPID generadas en el primer arranque y guardadas junto a los datos.
const VAPID_FILE = path.join(path.dirname(DATA_FILE), 'vapid.json');
let vapid;
try { vapid = JSON.parse(fs.readFileSync(VAPID_FILE, 'utf8')); } catch {
  vapid = webpush.generateVAPIDKeys();
  fs.mkdirSync(path.dirname(VAPID_FILE), { recursive: true });
  fs.writeFileSync(VAPID_FILE, JSON.stringify(vapid), { mode: 0o600 });
}
const VAPID_SUBJECT = process.env.VAPID_SUBJECT || process.env.PUBLIC_URL || 'mailto:muelles@rademacher.invalid';
webpush.setVapidDetails(VAPID_SUBJECT, vapid.publicKey, vapid.privateKey);
// Solo se envían avisos a servicios push conocidos (evita que el servidor llame a URLs arbitrarias).
const PUSH_HOSTS = [/(^|\.)push\.apple\.com$/, /^fcm\.googleapis\.com$/, /^android\.googleapis\.com$/,
  /(^|\.)push\.services\.mozilla\.com$/, /(^|\.)notify\.windows\.com$/];
function validSubscription(sub) {
  try {
    if (!sub || typeof sub.endpoint !== 'string' || !sub.keys || !sub.keys.p256dh || !sub.keys.auth) return false;
    if (JSON.stringify(sub).length > 2000) return false;
    const u = new URL(sub.endpoint);
    if (process.env.PUSH_ALLOW_ANY) return u.protocol === 'https:';
    return u.protocol === 'https:' && PUSH_HOSTS.some((re) => re.test(u.hostname));
  } catch { return false; }
}
// Textos traducidos: se reutiliza public/i18n.js (el mismo archivo que usa el navegador).
const I18N = (() => {
  const ctx = { window: {}, navigator: {} };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'public', 'i18n.js'), 'utf8'), ctx);
  return ctx.window.I18N;
})();
function tr(lang, key, vars = {}) {
  const t = (I18N[lang] && I18N[lang][key]) || I18N.es[key] || key;
  return String(t).replace(/\{(\w)\}/g, (_, k) => (vars[k] ?? ''));
}
let pushQueue = []; // [{visit, key}]
function queuePush(v, key) { if (v.push) pushQueue.push({ v, key }); }
function flushPush() {
  const byVisit = new Map();
  for (const item of pushQueue) {
    // Si en el mismo cambio le toca el turno, ese aviso sustituye a los demás.
    const prev = byVisit.get(item.v.id);
    if (!prev || item.key === 'st_turno') byVisit.set(item.v.id, item);
  }
  pushQueue = [];
  for (const { v, key } of byVisit.values()) {
    const sub = v.push;
    if (!sub) continue;
    const body = tr(v.lang || 'es', key, { d: v.dock });
    const payload = JSON.stringify({ title: `Rademacher · ${tr(v.lang || 'es', 'dock')} ${v.dock}`, body, url: `/c/${v.code}`, tag: v.code });
    webpush.sendNotification(sub, payload, { TTL: 3600, urgency: 'high' }).catch((err) => {
      if (err && (err.statusCode === 404 || err.statusCode === 410)) { // suscripción caducada
        if (v.push === sub) { v.push = null; save(); }
      } else console.warn('push error', err && (err.statusCode || err.message));
    });
  }
}

// ---------------------------------------------------------------- estado
function emptyState() {
  return {
    seq: 0,
    docks: Array.from({ length: DOCKS }, (_, i) => ({ id: i + 1, blocked: false, note: '' })),
    visits: [],
  };
}

function loadState() {
  try {
    const s = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
    // Ajustar número de muelles si cambió la configuración
    const docks = Array.from({ length: DOCKS }, (_, i) =>
      s.docks.find((d) => d.id === i + 1) || { id: i + 1, blocked: false, note: '' });
    return { seq: s.seq || 0, docks, visits: s.visits || [] };
  } catch {
    return emptyState();
  }
}

let state = loadState();
let version = 1;

function save() {
  fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true });
  const tmp = DATA_FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(state));
  fs.renameSync(tmp, DATA_FILE);
}

function newCode() {
  for (;;) {
    let c = '';
    const bytes = crypto.randomBytes(5);
    for (const b of bytes) c += CODE_ALPHABET[b % CODE_ALPHABET.length];
    if (!state.visits.some((v) => v.code === c)) return c;
  }
}

const now = () => Date.now();
const dockQueue = (dockId) =>
  state.visits.filter((v) => v.dock === dockId && ACTIVE.includes(v.status))
    .sort((a, b) => a.queuedAt - b.queuedAt);

/** Llama al siguiente chófer en cada muelle libre. */
function promote() {
  for (const d of state.docks) {
    if (d.blocked) continue;
    const q = dockQueue(d.id);
    if (q.some((v) => v.status === 'turno' || v.status === 'en_muelle')) continue;
    const next = q.find((v) => v.status === 'espera');
    if (next) { next.status = 'turno'; next.calledAt = now(); queuePush(next, 'st_turno'); }
  }
}

function commit() {
  promote();
  // Al cerrar la visita se borra la suscripción push (minimización de datos)
  for (const v of state.visits) if (!ACTIVE.includes(v.status) && v.push) v.push = null;
  save();
  version++;
  broadcast();
  flushPush();
}

function purge() {
  const limit = now() - RETENTION_DAYS * 86400000;
  const before = state.visits.length;
  state.visits = state.visits.filter((v) => ACTIVE.includes(v.status) || (v.closedAt || v.createdAt) > limit);
  if (state.visits.length !== before) commit();
}

// ---------------------------------------------------------------- vistas
function driverView(v) {
  const q = dockQueue(v.dock);
  const idx = q.findIndex((x) => x.id === v.id);
  const occupant = q.find((x) => x.status === 'turno' || x.status === 'en_muelle');
  const dock = state.docks.find((d) => d.id === v.dock);
  return {
    code: v.code, plate: v.plate, op: v.op, dock: v.dock, status: v.status,
    ahead: idx > 0 ? idx : 0,
    occupantPlate: occupant && occupant.id !== v.id ? occupant.plate : null,
    dockBlocked: !!(dock && dock.blocked),
    message: v.message || null,
    calledAt: v.calledAt || null, dockedAt: v.dockedAt || null, finishedAt: v.closedAt || null,
    version,
  };
}

function publicView() {
  return {
    version,
    docks: state.docks.map((d) => {
      const q = dockQueue(d.id);
      const cur = q.find((v) => v.status === 'turno' || v.status === 'en_muelle');
      return {
        id: d.id, blocked: d.blocked,
        current: cur ? { plate: cur.plate, status: cur.status, op: cur.op } : null,
        waiting: q.filter((v) => v.status === 'espera').map((v) => v.plate),
      };
    }),
  };
}

function kpis() {
  const start = new Date(); start.setHours(0, 0, 0, 0);
  const done = state.visits.filter((v) => v.status === 'finalizado' && v.closedAt >= start.getTime());
  const avg = (arr) => (arr.length ? Math.round(arr.reduce((a, b) => a + b, 0) / arr.length / 60000) : null);
  return {
    finishedToday: done.length,
    loadsToday: done.filter((v) => v.op === 'carga').length,
    unloadsToday: done.filter((v) => v.op === 'descarga').length,
    avgWaitMin: avg(done.filter((v) => v.calledAt).map((v) => v.calledAt - v.createdAt)),
    avgDockMin: avg(done.filter((v) => v.calledAt).map((v) => v.closedAt - (v.dockedAt || v.calledAt))),
    waitingNow: state.visits.filter((v) => v.status === 'espera').length,
  };
}

// La oficina ve si el chófer tiene avisos activados, pero nunca la suscripción en sí.
function officeVisit(v) { const { push, ...rest } = v; return { ...rest, pushOn: !!push }; }

function officeView() {
  return {
    version,
    docks: state.docks.map((d) => ({ ...d, queue: dockQueue(d.id).map(officeVisit) })),
    history: state.visits.filter((v) => !ACTIVE.includes(v.status))
      .sort((a, b) => b.closedAt - a.closedAt).slice(0, 50).map(officeVisit),
    kpis: kpis(),
  };
}

// ---------------------------------------------------------------- SSE
const clients = new Set();
function broadcast() {
  for (const res of clients) res.write(`event: update\ndata: ${version}\n\n`);
}
setInterval(() => { for (const res of clients) res.write(': ping\n\n'); }, 25000).unref();
setInterval(purge, 3600000).unref();

// ---------------------------------------------------------------- auth oficina
const tokens = new Map(); // token -> expiry
const failed = new Map(); // ip -> {n, until}
function isOffice(req) {
  const t = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  const exp = tokens.get(t);
  if (!exp) return false;
  if (exp < now()) { tokens.delete(t); return false; }
  return true;
}
function safeEqual(a, b) {
  const ha = crypto.createHash('sha256').update(a).digest();
  const hb = crypto.createHash('sha256').update(b).digest();
  return crypto.timingSafeEqual(ha, hb);
}

// ---------------------------------------------------------------- utilidades HTTP
function send(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(data);
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > 1e5) { reject(new Error('too large')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => { try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}); } catch { reject(new Error('bad json')); } });
    req.on('error', reject);
  });
}
const clean = (s, max = 60) => String(s == null ? '' : s).replace(/[\u0000-\u001f]/g, '').trim().slice(0, max);
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.webmanifest': 'application/manifest+json', '.ico': 'image/x-icon' };

// Manifiesto de la app instalable. start_url incluye el código del chófer para que, al
// abrir el icono de la pantalla de inicio (iPhone), aparezca directamente su turno.
function serveManifest(req, res) {
  const c = new URL(req.url, 'http://x').searchParams.get('c') || '';
  const start = /^[A-Za-z0-9]{3,10}$/.test(c) ? `/c/${c.toUpperCase()}` : '/chofer';
  const body = JSON.stringify({
    name: 'Rademacher Muelles', short_name: 'Muelles', start_url: start, scope: '/', display: 'standalone',
    background_color: '#0b3d91', theme_color: '#0b3d91', lang: 'es',
    icons: [{ src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
      { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
      { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }],
  });
  res.writeHead(200, { 'Content-Type': 'application/manifest+json; charset=utf-8', 'Cache-Control': 'no-cache' });
  res.end(body);
}

function serveStatic(req, res, pathname) {
  if (pathname === '/manifest.webmanifest') return serveManifest(req, res);
  let file = pathname;
  if (file === '/') file = '/index.html';
  else if (/^\/c\/[A-Za-z0-9]+\/?$/.test(file) || file === '/chofer') file = '/chofer.html';
  else if (file === '/oficina') file = '/oficina.html';
  else if (file === '/pantalla') file = '/pantalla.html';
  const full = path.normalize(path.join(PUBLIC_DIR, file));
  if (!full.startsWith(PUBLIC_DIR + path.sep)) { res.writeHead(403); return res.end(); }
  fs.readFile(full, (err, data) => {
    if (err) { res.writeHead(404, { 'Content-Type': 'text/plain' }); return res.end('404'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(full)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    res.end(data);
  });
}

// ---------------------------------------------------------------- API
async function api(req, res, pathname) {
  const m = req.method;
  let match;

  if (m === 'GET' && pathname === '/api/stream') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive', 'X-Accel-Buffering': 'no' });
    res.write(`event: update\ndata: ${version}\n\n`);
    clients.add(res);
    req.on('close', () => clients.delete(res));
    return;
  }

  if (m === 'GET' && pathname === '/api/health') return send(res, 200, { ok: true, version });
  if (m === 'GET' && pathname === '/api/push/key') return send(res, 200, { publicKey: vapid.publicKey });
  if (m === 'GET' && pathname === '/api/public') return send(res, 200, publicView());

  // ---- Chófer
  if ((match = pathname.match(/^\/api\/driver\/([A-Za-z0-9]{3,10})(?:\/(arrive|finish|ack|lang|push))?$/))) {
    const code = match[1].toUpperCase();
    const v = state.visits.find((x) => x.code === code);
    if (!v) return send(res, 404, { error: 'not_found' });
    const action = match[2];
    if (m === 'GET' && !action) return send(res, 200, { ...driverView(v), pushOn: !!v.push });
    if (m !== 'POST' || !action) return send(res, 405, { error: 'method' });
    const body = await readBody(req).catch(() => ({}));
    if (action === 'lang') { v.lang = clean(body.lang, 5); save(); return send(res, 200, { ok: true }); }
    if (action === 'push') {
      if (!ACTIVE.includes(v.status)) return send(res, 409, { error: 'closed' });
      if (!validSubscription(body.subscription)) return send(res, 400, { error: 'bad_subscription' });
      v.push = { endpoint: body.subscription.endpoint, keys: { p256dh: String(body.subscription.keys.p256dh), auth: String(body.subscription.keys.auth) } };
      save();
      return send(res, 200, { ok: true });
    }
    if (action === 'ack') { if (v.message) v.message.read = true; commit(); return send(res, 200, driverView(v)); }
    if (action === 'arrive') {
      if (v.status !== 'turno') return send(res, 409, { error: 'not_your_turn', view: driverView(v) });
      v.status = 'en_muelle'; v.dockedAt = now(); commit();
      return send(res, 200, driverView(v));
    }
    if (action === 'finish') {
      if (v.status !== 'turno' && v.status !== 'en_muelle') return send(res, 409, { error: 'not_your_turn', view: driverView(v) });
      v.status = 'finalizado'; v.closedAt = now(); v.closedBy = 'chofer'; commit();
      return send(res, 200, driverView(v));
    }
  }

  // ---- Oficina: login
  if (m === 'POST' && pathname === '/api/office/login') {
    const fwd = process.env.TRUST_PROXY ? String(req.headers['x-forwarded-for'] || '').split(',')[0].trim() : '';
    const ip = fwd || req.socket.remoteAddress || '?';
    const f = failed.get(ip);
    if (f && f.until > now()) return send(res, 429, { error: 'too_many_attempts' });
    const body = await readBody(req).catch(() => ({}));
    if (!safeEqual(String(body.pin || ''), OFFICE_PIN)) {
      const n = (f ? f.n : 0) + 1;
      failed.set(ip, { n, until: n >= 5 ? now() + 60000 : 0 });
      return send(res, 401, { error: 'bad_pin' });
    }
    failed.delete(ip);
    const token = crypto.randomBytes(24).toString('hex');
    tokens.set(token, now() + 12 * 3600000);
    return send(res, 200, { token });
  }

  if (pathname.startsWith('/api/office/')) {
    if (!isOffice(req)) return send(res, 401, { error: 'unauthorized' });

    if (m === 'GET' && pathname === '/api/office/state') return send(res, 200, officeView());

    if (m === 'POST' && pathname === '/api/office/visits') {
      const b = await readBody(req).catch(() => null);
      if (!b) return send(res, 400, { error: 'bad_json' });
      const plate = clean(b.plate, 15).toUpperCase();
      const dock = Number(b.dock);
      if (!plate) return send(res, 400, { error: 'plate_required' });
      if (!state.docks.some((d) => d.id === dock)) return send(res, 400, { error: 'bad_dock' });
      const v = {
        id: ++state.seq, code: newCode(), plate,
        trailer: clean(b.trailer, 15).toUpperCase(), driver: clean(b.driver), carrier: clean(b.carrier),
        phone: clean(b.phone, 20), op: b.op === 'carga' ? 'carga' : 'descarga',
        alcohol: !!b.alcohol, notes: clean(b.notes, 200), lang: clean(b.lang, 5) || null,
        dock, status: 'espera', createdAt: now(), queuedAt: now(), message: null,
      };
      state.visits.push(v);
      commit();
      return send(res, 201, officeVisit(v));
    }

    if ((match = pathname.match(/^\/api\/office\/visits\/(\d+)$/)) && m === 'POST') {
      const v = state.visits.find((x) => x.id === Number(match[1]));
      if (!v) return send(res, 404, { error: 'not_found' });
      const b = await readBody(req).catch(() => ({}));
      switch (b.action) {
        case 'move': {
          const dock = Number(b.dock);
          if (!state.docks.some((d) => d.id === dock)) return send(res, 400, { error: 'bad_dock' });
          if (!ACTIVE.includes(v.status)) return send(res, 409, { error: 'closed' });
          if (dock !== v.dock) {
            v.dock = dock; v.status = 'espera'; v.queuedAt = now(); v.calledAt = null; v.dockedAt = null;
            v.message = { key: 'm_dock', at: now(), read: false };
            queuePush(v, 'm_dock');
          }
          break;
        }
        case 'up': { // adelantar un puesto en la cola
          const q = dockQueue(v.dock).filter((x) => x.status === 'espera');
          const i = q.findIndex((x) => x.id === v.id);
          if (i > 0) { const t = q[i - 1].queuedAt; q[i - 1].queuedAt = v.queuedAt; v.queuedAt = t; }
          break;
        }
        case 'docked': if (v.status === 'turno') { v.status = 'en_muelle'; v.dockedAt = now(); } break;
        case 'finish':
          if (!ACTIVE.includes(v.status)) return send(res, 409, { error: 'closed' });
          v.status = 'finalizado'; v.closedAt = now(); v.closedBy = 'oficina';
          if (!v.calledAt) v.calledAt = v.closedAt;
          break;
        case 'cancel':
          if (!ACTIVE.includes(v.status)) return send(res, 409, { error: 'closed' });
          v.status = 'cancelado'; v.closedAt = now(); v.closedBy = 'oficina'; break;
        case 'message':
          if (!MESSAGES.includes(b.key)) return send(res, 400, { error: 'bad_message' });
          v.message = { key: b.key, at: now(), read: false }; queuePush(v, b.key); break;
        default: return send(res, 400, { error: 'bad_action' });
      }
      commit();
      return send(res, 200, officeVisit(v));
    }

    if ((match = pathname.match(/^\/api\/office\/docks\/(\d+)$/)) && m === 'POST') {
      const d = state.docks.find((x) => x.id === Number(match[1]));
      if (!d) return send(res, 404, { error: 'not_found' });
      const b = await readBody(req).catch(() => ({}));
      if (typeof b.blocked === 'boolean') d.blocked = b.blocked;
      if (b.note !== undefined) d.note = clean(b.note, 80);
      commit();
      return send(res, 200, d);
    }
  }

  return send(res, 404, { error: 'not_found' });
}

const server = http.createServer((req, res) => {
  const { pathname } = new URL(req.url, 'http://localhost');
  if (pathname.startsWith('/api/')) {
    api(req, res, pathname).catch((e) => { console.error(e); if (!res.headersSent) send(res, 500, { error: 'server' }); });
  } else if (req.method === 'GET' || req.method === 'HEAD') {
    serveStatic(req, res, pathname);
  } else { res.writeHead(405); res.end(); }
});

if (require.main === module) {
  purge();
  server.listen(PORT, () => {
    console.log(`Rademacher · Muelles en http://localhost:${PORT}  (${DOCKS} muelles)`);
    if (!process.env.OFFICE_PIN) console.warn('AVISO: usando PIN de oficina por defecto (1234). Defina OFFICE_PIN.');
  });
}

module.exports = { server };
