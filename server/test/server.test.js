'use strict';

// Smoke tests against the real server process (no Google access needed: only paths that fail before any key fetch).

const test = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

let nextPort = 3300 + Math.floor(Math.random() * 400);

async function startServer(env = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rucek-test-'));
  const port = nextPort++;
  const child = spawn(process.execPath, [path.join(__dirname, '..', 'server.js')], {
    env: { PATH: process.env.PATH, PORT: String(port), DB_PATH: path.join(dir, 'test.db'), ADMIN_PASSWORD: 'test-password-123', ...env },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let out = '';
  child.stdout.on('data', (d) => (out += d));
  child.stderr.on('data', (d) => (out += d));
  const deadline = Date.now() + 10_000;
  while (!out.includes('Accounts:')) {
    if (child.exitCode !== null) throw new Error('server exited early:\n' + out);
    if (Date.now() > deadline) throw new Error('server did not start:\n' + out);
    await new Promise((r) => setTimeout(r, 50));
  }
  const base = `http://127.0.0.1:${port}`;
  return {
    base, log: () => out,
    async stop() { child.kill(); await new Promise((r) => child.once('exit', r)); fs.rmSync(dir, { recursive: true, force: true }); },
  };
}
const call = async (srv, method, p, headers = {}) => {
  const res = await fetch(srv.base + p, { method, headers });
  return { status: res.status, body: await res.json().catch(() => null) };
};

test('accounts not configured: /api/session and /api/me answer 503, the rest still works', async () => {
  const srv = await startServer();
  try {
    assert.match(srv.log(), /Accounts: off/);
    assert.equal((await call(srv, 'POST', '/api/session', { authorization: 'Bearer a.b.c' })).status, 503);
    assert.equal((await call(srv, 'GET', '/api/me', { authorization: 'Bearer a.b.c' })).status, 503);
    assert.equal((await call(srv, 'GET', '/health')).status, 200);
    const cat = await call(srv, 'GET', '/api/catalog?since=0');
    assert.equal(cat.status, 200);
    assert.deepEqual(Object.keys(cat.body).sort(), ['ingredients', 'recipes', 'serverTime']);
  } finally { await srv.stop(); }
});

test('accounts configured: no or bad credentials are 401, wrong methods are 404, no network is touched', async () => {
  const srv = await startServer({ FIREBASE_PROJECT_ID: 'test-project' });
  try {
    assert.match(srv.log(), /Accounts: on \(project test-project\)/);
    assert.equal((await call(srv, 'POST', '/api/session')).status, 401);
    assert.equal((await call(srv, 'GET', '/api/me')).status, 401);
    assert.equal((await call(srv, 'POST', '/api/session', { authorization: 'Bearer not-a-jwt' })).status, 401);
    assert.equal((await call(srv, 'POST', '/api/session', { authorization: 'Basic abc' })).status, 401);
    assert.equal((await call(srv, 'GET', '/api/session')).status, 404);
    assert.equal((await call(srv, 'POST', '/api/me')).status, 404);
    const r = await call(srv, 'POST', '/api/session', { authorization: 'Bearer not-a-jwt' });
    assert.ok(r.body.error && !/jwt|malformed/i.test(r.body.error)); // vague message
  } finally { await srv.stop(); }
});

test('App Check without a project number stays off; an invalid mode falls back to off', async () => {
  const a = await startServer({ FIREBASE_PROJECT_ID: 'p', APPCHECK_MODE: 'monitor' });
  try { assert.match(a.log(), /App Check stays off/); assert.match(a.log(), /App Check: off/); } finally { await a.stop(); }
  const b = await startServer({ FIREBASE_PROJECT_ID: 'p', FIREBASE_PROJECT_NUMBER: '123', APPCHECK_MODE: 'bogus' });
  try { assert.match(b.log(), /must be off, monitor or enforce/); assert.match(b.log(), /App Check: off/); } finally { await b.stop(); }
  const c = await startServer({ FIREBASE_PROJECT_ID: 'p', FIREBASE_PROJECT_NUMBER: '123' });
  try { assert.match(c.log(), /App Check: monitor/); } finally { await c.stop(); }
});

test('the admin login limiter keys on the real client address (CF-Connecting-IP)', async () => {
  const srv = await startServer();
  try {
    const attempt = (ip) => fetch(srv.base + '/admin/api/login', {
      method: 'POST', headers: { 'content-type': 'application/json', 'cf-connecting-ip': ip }, body: JSON.stringify({ password: 'wrong' }),
    }).then((r) => r.status);
    for (let i = 0; i < 5; i++) assert.equal(await attempt('8.8.8.8'), 401);
    assert.equal(await attempt('8.8.8.8'), 429);   // that client is locked out...
    assert.equal(await attempt('9.9.9.9'), 401);   // ...but another one behind the same Cloudflare edge is not
  } finally { await srv.stop(); }
});

test('DELETE /api/me needs a valid sign-in: without one it is 401 (503 when accounts are off), other methods stay 404', async () => {
  const off = await startServer();
  try { assert.equal((await call(off, 'DELETE', '/api/me', { authorization: 'Bearer a.b.c' })).status, 503); } finally { await off.stop(); }
  const srv = await startServer({ FIREBASE_PROJECT_ID: 'test-project' });
  try {
    assert.equal((await call(srv, 'DELETE', '/api/me')).status, 401);
    assert.equal((await call(srv, 'DELETE', '/api/me', { authorization: 'Bearer not-a-jwt' })).status, 401);
    assert.equal((await call(srv, 'DELETE', '/api/session', { authorization: 'Bearer x' })).status, 404);
    assert.equal((await call(srv, 'PUT', '/api/me')).status, 404);
  } finally { await srv.stop(); }
});
