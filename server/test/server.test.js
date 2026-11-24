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

test('the new list endpoints need a sign-in (503 when accounts are off, 401 without a valid token), wrong methods are 404', async () => {
  const off = await startServer();
  try {
    assert.equal((await call(off, 'POST', '/api/generations', { authorization: 'Bearer a.b.c' })).status, 503);
    assert.equal((await call(off, 'GET', '/api/unlocks', { authorization: 'Bearer a.b.c' })).status, 503);
  } finally { await off.stop(); }
  const srv = await startServer({ FIREBASE_PROJECT_ID: 'test-project' });
  try {
    assert.equal((await call(srv, 'POST', '/api/generations')).status, 401);
    assert.equal((await call(srv, 'POST', '/api/generations', { authorization: 'Bearer not-a-jwt' })).status, 401);
    assert.equal((await call(srv, 'GET', '/api/unlocks')).status, 401);
    assert.equal((await call(srv, 'GET', '/api/unlocks', { authorization: 'Bearer not-a-jwt' })).status, 401);
    assert.equal((await call(srv, 'GET', '/api/generations')).status, 404);
    assert.equal((await call(srv, 'POST', '/api/unlocks')).status, 404);
  } finally { await srv.stop(); }
});

async function adminCookie(srv) {
  const res = await fetch(srv.base + '/admin/api/login', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ password: 'test-password-123' }),
  });
  return res.headers.get('set-cookie').split(';')[0];
}
const RECIPE = {
  name: 'Тест рецепт', description: 'тајниот опис', servings: 3, prepTimeMinutes: 5, cookTimeMinutes: 20, category: 'PASTA',
  tags: ['брзо'], instructionSteps: ['сецкај', 'вари'], notes: 'тајна забелешка',
  ingredients: [{ name: 'Паста', quantity: 300, unit: 'GRAM' }],
};
async function addRecipe(srv) {
  const cookie = await adminCookie(srv);
  const res = await fetch(srv.base + '/admin/api/recipes', { method: 'POST', headers: { 'content-type': 'application/json', cookie }, body: JSON.stringify(RECIPE) });
  assert.equal(res.status, 201);
}

test('the public catalog sends everything by default (with locked:false), as before', async () => {
  const srv = await startServer();
  try {
    await addRecipe(srv);
    const r = (await call(srv, 'GET', '/api/catalog?since=0')).body.recipes[0];
    assert.equal(r.description, 'тајниот опис');
    assert.deepEqual(r.instructionSteps, ['сецкај', 'вари']);
    assert.equal(r.locked, false);
  } finally { await srv.stop(); }
});

test('with CATALOG_LOCKED=1 the public catalog keeps name, times and ingredients but not description, steps, notes or tags', async () => {
  const srv = await startServer({ CATALOG_LOCKED: '1' });
  try {
    await addRecipe(srv);
    const text = JSON.stringify((await call(srv, 'GET', '/api/catalog?since=0')).body);
    for (const secret of ['тајниот опис', 'сецкај', 'вари', 'тајна забелешка', 'брзо']) assert.ok(!text.includes(secret), secret);
    const r = JSON.parse(text).recipes[0];
    assert.equal(r.name, 'Тест рецепт');
    assert.equal(r.cookTimeMinutes, 20);
    assert.equal(r.ingredients[0].name, 'Паста');
    assert.equal(r.locked, true);
    assert.deepEqual(r.instructionSteps, []);
  } finally { await srv.stop(); }
});

test('changing a plan by hand needs the admin login and a real registered user', async () => {
  const srv = await startServer();
  try {
    const put = (cookie, body) => fetch(srv.base + '/admin/api/users/plan', {
      method: 'PUT', headers: { 'content-type': 'application/json', ...(cookie ? { cookie } : {}) }, body: JSON.stringify(body),
    }).then((r) => r.status);
    assert.equal(await put(null, { userId: 'usr_x', plan: 'PAID' }), 401);
    const cookie = await adminCookie(srv);
    assert.equal(await put(cookie, { userId: 'usr_unknown', plan: 'PAID' }), 404);
  } finally { await srv.stop(); }
});

test('the public delete-account page (needed for Google Play) is served as plain, safe HTML with the contact address', async () => {
  const srv = await startServer();
  try {
    for (const p of ['/delete-account', '/delete-account/']) {
      const res = await fetch(srv.base + p);
      assert.equal(res.status, 200);
      assert.match(res.headers.get('content-type'), /text\/html/);
      assert.match(res.headers.get('content-security-policy'), /default-src 'none'/);
      assert.equal(res.headers.get('x-frame-options'), 'DENY');
      const html = await res.text();
      // Cloudflare rewrites any plain e-mail address into a script-decoded placeholder, which a page without scripts
      // would show as "[email protected]". So the @ and the dot are written as HTML entities, which browsers show as the
      // address and Cloudflare does not touch.
      assert.ok(!html.includes('@'), 'no plain @ in the page (Cloudflare would obfuscate it)');
      const decoded = html.replace(/&#([0-9]+);/g, (_, n) => String.fromCharCode(Number(n)));
      assert.ok(decoded.includes('mojrucek@gmail.com'), 'the contact address is on the page');
      // No mailto: link either: Cloudflare turns every one into a /cdn-cgi/ link and adds a script our page does not allow.
      assert.ok(!/mailto:/i.test(html), 'no mailto link');
      assert.ok(html.includes('Избриши сметка'), 'it names the button in the app');
      assert.ok(html.includes('Delete your account'), 'it has an English part');
      assert.ok(!/<script/i.test(html), 'no scripts');
    }
    assert.equal((await fetch(srv.base + '/delete-account', { method: 'POST' })).status, 404);
    assert.equal((await fetch(srv.base + '/delete-account', { method: 'HEAD' })).status, 200);
    // the page needs no sign-in and does not unlock anything else
    assert.equal((await fetch(srv.base + '/admin/api/recipes')).status, 401);
  } finally { await srv.stop(); }
});

test('the public privacy policy (Macedonian) is served as plain, safe HTML, with the contact address and without Cloudflare-triggering parts', async () => {
  const srv = await startServer();
  try {
    for (const p of ['/privacy', '/privacy/']) {
      const res = await fetch(srv.base + p);
      assert.equal(res.status, 200);
      assert.match(res.headers.get('content-type'), /text\/html/);
      assert.match(res.headers.get('content-security-policy'), /default-src 'none'/);
      assert.equal(res.headers.get('x-frame-options'), 'DENY');
      const html = await res.text();
      assert.match(html, /<html lang="mk">/);
      assert.ok(!html.includes('@'), 'no plain @ (Cloudflare would obfuscate it)');
      assert.ok(!/mailto:/i.test(html), 'no mailto link');
      assert.ok(!/<script/i.test(html), 'no scripts');
      const decoded = html.replace(/&#([0-9]+);/g, (_, n) => String.fromCharCode(Number(n)));
      assert.ok(decoded.includes('mojrucek@gmail.com'), 'the contact address is on the page');
      for (const heading of ['Кои податоци се користат', 'Со кого се споделуваат', 'Колку долго се чуваат', 'Твои права', 'Деца']) {
        assert.ok(html.includes(heading), heading);
      }
      assert.ok(html.includes('href="/delete-account"'), 'it points to the deletion page');
    }
    assert.equal((await fetch(srv.base + '/privacy', { method: 'POST' })).status, 404);
  } finally { await srv.stop(); }
});
