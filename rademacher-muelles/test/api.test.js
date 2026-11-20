'use strict';
const test = require('node:test');
const assert = require('node:assert');
const os = require('os');
const path = require('path');

process.env.DATA_FILE = path.join(os.tmpdir(), `muelles-test-${process.pid}.json`);
process.env.OFFICE_PIN = '9999';
const { server } = require('../server');

let base, token;
const j = async (url, opts = {}) => {
  const r = await fetch(base + url, { ...opts, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: opts.body ? JSON.stringify(opts.body) : undefined });
  return { status: r.status, body: await r.json() };
};

test.before(() => new Promise((ok) => server.listen(0, () => { base = `http://127.0.0.1:${server.address().port}`; ok(); })));
test.after(() => { server.closeAllConnections?.(); server.close(); });

test('PIN incorrecto rechazado y correcto aceptado', async () => {
  assert.equal((await j('/api/office/login', { method: 'POST', body: { pin: '0000' } })).status, 401);
  const r = await j('/api/office/login', { method: 'POST', body: { pin: '9999' } });
  assert.equal(r.status, 200); token = r.body.token;
});

test('turnos en el mismo muelle: el segundo pasa a turno cuando el primero finaliza', async () => {
  const a = (await j('/api/office/visits', { method: 'POST', body: { plate: '1234abc', dock: 3, op: 'descarga' } })).body;
  const b = (await j('/api/office/visits', { method: 'POST', body: { plate: '5678def', dock: 3, op: 'carga' } })).body;
  assert.equal(a.plate, '1234ABC');
  const saved = token; token = null; // el chófer no usa token

  let va = (await j(`/api/driver/${a.code}`)).body;
  let vb = (await j(`/api/driver/${b.code}`)).body;
  assert.equal(va.status, 'turno');
  assert.equal(vb.status, 'espera'); assert.equal(vb.ahead, 1); assert.equal(vb.occupantPlate, '1234ABC');

  // B no puede finalizar si no es su turno
  assert.equal((await j(`/api/driver/${b.code}/finish`, { method: 'POST' })).status, 409);

  assert.equal((await j(`/api/driver/${a.code}/arrive`, { method: 'POST' })).body.status, 'en_muelle');
  assert.equal((await j(`/api/driver/${a.code}/finish`, { method: 'POST' })).body.status, 'finalizado');

  vb = (await j(`/api/driver/${b.code}`)).body;
  assert.equal(vb.status, 'turno'); assert.equal(vb.ahead, 0);
  token = saved;
});

test('cambio de muelle y mensajes traducibles', async () => {
  const v = (await j('/api/office/visits', { method: 'POST', body: { plate: 'X1', dock: 5 } })).body;
  await j('/api/office/visits', { method: 'POST', body: { plate: 'X2', dock: 5 } });
  const x2 = (await j('/api/office/state')).body.docks[4].queue[1];
  await j(`/api/office/visits/${x2.id}`, { method: 'POST', body: { action: 'move', dock: 6 } });
  await j(`/api/office/visits/${v.id}`, { method: 'POST', body: { action: 'message', key: 'm_docs' } });
  const st = (await j('/api/office/state')).body;
  assert.equal(st.docks[5].queue[0].status, 'turno');
  assert.equal(st.docks[5].queue[0].message.key, 'm_dock');
  assert.equal(st.docks[4].queue[0].message.key, 'm_docs');
  assert.equal((await j(`/api/office/visits/${v.id}`, { method: 'POST', body: { action: 'message', key: 'hack' } })).status, 400);
});

test('muelle cerrado no llama a nadie', async () => {
  await j('/api/office/docks/9', { method: 'POST', body: { blocked: true } });
  await j('/api/office/visits', { method: 'POST', body: { plate: 'B1', dock: 9 } });
  assert.equal((await j('/api/office/state')).body.docks[8].queue[0].status, 'espera');
  await j('/api/office/docks/9', { method: 'POST', body: { blocked: false } });
  assert.equal((await j('/api/office/state')).body.docks[8].queue[0].status, 'turno');
});

test('endpoints de oficina requieren token', async () => {
  const saved = token; token = null;
  assert.equal((await j('/api/office/state')).status, 401);
  token = saved;
});
