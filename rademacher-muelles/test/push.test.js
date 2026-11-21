'use strict';
// Prueba de avisos push de extremo a extremo: un servicio push simulado (HTTPS local) recibe el
// aviso cifrado y se descifra con las claves del "móvil" para comprobar el texto y el idioma.
const test = require('node:test');
const assert = require('node:assert');
const os = require('os');
const fs = require('fs');
const path = require('path');
const https = require('https');
const crypto = require('crypto');
const { execFileSync } = require('child_process');
const ece = require('http_ece');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'muelles-push-'));
process.env.DATA_FILE = path.join(tmp, 'state.json');
process.env.OFFICE_PIN = '9999';
process.env.PUSH_ALLOW_ANY = '1';                 // permitir el servicio push simulado
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';   // certificado autofirmado del simulador
execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-subj', '/CN=localhost', '-days', '1',
  '-keyout', path.join(tmp, 'k.pem'), '-out', path.join(tmp, 'c.pem')], { stdio: 'ignore' });
const { server } = require('../server');

let base, token, pushBase;
const received = [];
const pushServer = https.createServer({ key: fs.readFileSync(path.join(tmp, 'k.pem')), cert: fs.readFileSync(path.join(tmp, 'c.pem')) },
  (req, res) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      received.push({ path: req.url, headers: req.headers, body: Buffer.concat(chunks) });
      res.writeHead(req.url.includes('gone') ? 410 : 201); res.end();
    });
  });

const j = async (url, opts = {}) => {
  const r = await fetch(base + url, { ...opts, headers: { 'Content-Type': 'application/json', ...(opts.auth ? { Authorization: 'Bearer ' + token } : {}) },
    body: opts.body ? JSON.stringify(opts.body) : undefined });
  return { status: r.status, body: await r.json().catch(() => null) };
};
const waitFor = async (n) => { for (let i = 0; i < 50 && received.length < n; i++) await new Promise((r) => setTimeout(r, 50)); };

// "Móvil": par de claves ECDH P-256 y secreto de autenticación, como genera el navegador
function device() {
  const ecdh = crypto.createECDH('prime256v1'); ecdh.generateKeys();
  const auth = crypto.randomBytes(16);
  return { ecdh, auth, keys: { p256dh: ecdh.getPublicKey().toString('base64url'), auth: auth.toString('base64url') } };
}
const decrypt = (dev, body) => JSON.parse(ece.decrypt(body, { version: 'aes128gcm', privateKey: dev.ecdh, authSecret: dev.auth }).toString());

test.before(async () => {
  await new Promise((ok) => server.listen(0, ok)); base = `http://127.0.0.1:${server.address().port}`;
  await new Promise((ok) => pushServer.listen(0, ok)); pushBase = `https://127.0.0.1:${pushServer.address().port}`;
  token = (await j('/api/office/login', { method: 'POST', body: { pin: '9999' } })).body.token;
});
test.after(() => { server.closeAllConnections?.(); server.close(); pushServer.closeAllConnections?.(); pushServer.close(); });

test('manifiesto de la app instalable con el código del chófer', async () => {
  const r = await fetch(base + '/manifest.webmanifest?c=abc12');
  assert.match(r.headers.get('content-type'), /manifest\+json/);
  const m = await r.json();
  assert.equal(m.start_url, '/c/ABC12'); assert.equal(m.display, 'standalone');
  assert.equal((await (await fetch(base + '/manifest.webmanifest?c=../x')).json()).start_url, '/chofer');
  assert.equal((await fetch(base + '/sw.js')).status, 200);
  assert.equal((await fetch(base + '/apple-touch-icon.png')).status, 200);
});

test('rechaza suscripciones a URLs que no son servicios push', async () => {
  delete process.env.PUSH_ALLOW_ANY;
  const v = (await j('/api/office/visits', { method: 'POST', auth: true, body: { plate: 'SEC1', dock: 4 } })).body;
  const bad = { endpoint: 'https://intranet.local/x', keys: device().keys };
  assert.equal((await j(`/api/driver/${v.code}/push`, { method: 'POST', body: { subscription: bad } })).status, 400);
  const apple = { endpoint: 'https://web.push.apple.com/QWxhZGRpbg', keys: device().keys };
  assert.equal((await j(`/api/driver/${v.code}/push`, { method: 'POST', body: { subscription: apple } })).status, 200);
  process.env.PUSH_ALLOW_ANY = '1';
  // La oficina sabe que tiene avisos, pero no ve la suscripción
  const q = (await j('/api/office/state', { auth: true })).body.docks[3].queue[0];
  assert.equal(q.pushOn, true); assert.equal(q.push, undefined);
  await j(`/api/office/visits/${v.id}`, { method: 'POST', auth: true, body: { action: 'cancel' } });
});

test('aviso de turno cifrado, en el idioma del chófer, al finalizar el anterior', async () => {
  const a = (await j('/api/office/visits', { method: 'POST', auth: true, body: { plate: 'P1', dock: 2 } })).body;
  const b = (await j('/api/office/visits', { method: 'POST', auth: true, body: { plate: 'P2', dock: 2 } })).body;
  const dev = device();
  await j(`/api/driver/${b.code}/lang`, { method: 'POST', body: { lang: 'ro' } });
  assert.equal((await j(`/api/driver/${b.code}/push`, { method: 'POST', body: { subscription: { endpoint: pushBase + '/sub-b', keys: dev.keys } } })).status, 200);
  assert.equal((await j(`/api/driver/${b.code}`)).body.pushOn, true);

  received.length = 0;
  await j(`/api/driver/${a.code}/finish`, { method: 'POST' });
  await waitFor(1);
  assert.equal(received.length, 1);
  const msg = received[0];
  assert.equal(msg.path, '/sub-b');
  assert.equal(msg.headers['content-encoding'], 'aes128gcm');
  assert.match(msg.headers.authorization, /^vapid t=.+, k=.+/);
  const payload = decrypt(dev, msg.body);
  assert.equal(payload.body, 'ESTE RÂNDUL DUMNEAVOASTRĂ! Mergeți la rampa 2');
  assert.equal(payload.url, `/c/${b.code}`);
  assert.match(payload.title, /Rampă 2/);

  // Mensaje de oficina → aviso traducido
  received.length = 0;
  await j(`/api/office/visits/${b.id}`, { method: 'POST', auth: true, body: { action: 'message', key: 'm_docs' } });
  await waitFor(1);
  assert.equal(decrypt(dev, received[0].body).body, 'Documentele dvs. sunt gata. Ridicați-le de la birou.');

  // Al finalizar se borra la suscripción: ya no hay más avisos
  await j(`/api/driver/${b.code}/finish`, { method: 'POST' });
  assert.equal((await j(`/api/driver/${b.code}`)).body.pushOn, false);
});

test('suscripción caducada (410) se elimina', async () => {
  const a = (await j('/api/office/visits', { method: 'POST', auth: true, body: { plate: 'G1', dock: 8 } })).body;
  const b = (await j('/api/office/visits', { method: 'POST', auth: true, body: { plate: 'G2', dock: 8 } })).body;
  await j(`/api/driver/${b.code}/push`, { method: 'POST', body: { subscription: { endpoint: pushBase + '/gone', keys: device().keys } } });
  received.length = 0;
  await j(`/api/driver/${a.code}/finish`, { method: 'POST' });
  await waitFor(1);
  await new Promise((r) => setTimeout(r, 200));
  assert.equal((await j(`/api/driver/${b.code}`)).body.pushOn, false);
});
