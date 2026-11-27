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

test('the sync endpoints need a sign-in (503 when accounts are off, 401 without a valid token), wrong methods are 404', async () => {
  const off = await startServer();
  try {
    for (const [m, p] of [['GET', '/api/sync'], ['GET', '/api/sync/data'], ['PUT', '/api/sync']]) {
      assert.equal((await call(off, m, p, { authorization: 'Bearer a.b.c' })).status, 503, `${m} ${p}`);
    }
  } finally { await off.stop(); }
  const srv = await startServer({ FIREBASE_PROJECT_ID: 'test-project' });
  try {
    for (const [m, p] of [['GET', '/api/sync'], ['GET', '/api/sync/data'], ['PUT', '/api/sync']]) {
      assert.equal((await call(srv, m, p)).status, 401, `${m} ${p} without token`);
      assert.equal((await call(srv, m, p, { authorization: 'Bearer not-a-jwt' })).status, 401, `${m} ${p} bad token`);
    }
    assert.equal((await call(srv, 'POST', '/api/sync')).status, 404);
    assert.equal((await call(srv, 'DELETE', '/api/sync')).status, 404);
    assert.equal((await call(srv, 'PUT', '/api/sync/data')).status, 404);
  } finally { await srv.stop(); }
});

test('the public pages tell the truth about sync: a copy of the data is kept on the server and is deleted with the account', async () => {
  const srv = await startServer();
  try {
    const privacy = await (await fetch(srv.base + '/privacy')).text();
    const del = await (await fetch(srv.base + '/delete-account')).text();
    for (const [name, html] of [['privacy', privacy], ['delete-account', del]]) {
      assert.ok(html.includes('копија'), `${name}: mentions the copy on the server`);
      assert.ok(!html.includes('остануваат на твојот телефон, а лозинките'), `${name}: the old "stays only on the phone" sentence is gone`);
      assert.ok(!html.includes('Не ги чуваме твоите намирници'), `${name}: no claim that pantry and recipes are not stored`);
      assert.ok(!html.includes('We do not store your pantry'), `${name}: no English claim either`);
    }
    assert.ok(privacy.includes('копијата на твоите податоци') && privacy.includes('копијата на податоците на серверот'), 'retention and deletion cover the copy');
    assert.ok(privacy.includes('Поставки на телефонот'), 'settings are said to stay on the phone');
    assert.ok(del.includes('pantry, own recipes, plans, shopping list, favourites'), 'the English part lists what the copy contains');
  } finally { await srv.stop(); }
});

// ---------- recipe pictures ----------

// Tiny but real files (just their first bytes matter to the server).
const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 16]), Buffer.from('JFIF\0pretend-photo-data-of-a-jpeg'), Buffer.from([0xff, 0xd9])]);
const PNG = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.from('pretend-png-data-0000')]);
const WEBP = Buffer.concat([Buffer.from('RIFF'), Buffer.from([0x20, 0, 0, 0]), Buffer.from('WEBPVP8 pretend-webp-data')]);

async function newRecipe(srv, cookie, name = 'Тест рецепт') {
  const res = await fetch(srv.base + '/admin/api/recipes', { method: 'POST', headers: { 'content-type': 'application/json', cookie }, body: JSON.stringify({ ...RECIPE, name }) });
  assert.equal(res.status, 201);
  return res.json();
}
const putImage = (srv, cookie, id, body) => fetch(`${srv.base}/admin/api/recipes/${id}/image`, { method: 'PUT', headers: { cookie, 'content-type': 'application/octet-stream' }, body });

test('an admin can add a picture to a recipe; it appears in the catalog and is served publicly with a year of caching', async () => {
  const srv = await startServer();
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    assert.equal(r.imageUrl, null, 'no picture yet');

    const put = await putImage(srv, cookie, r.id, JPEG);
    assert.equal(put.status, 200);
    const { imageUrl } = await put.json();
    assert.match(imageUrl, new RegExp(`^/img/${r.id}-[0-9a-f]{16}\\.jpg$`));

    const catalog = (await call(srv, 'GET', '/api/catalog?since=0')).body.recipes.find((x) => x.id === r.id);
    assert.equal(catalog.imageUrl, imageUrl);

    const pic = await fetch(srv.base + imageUrl);                      // public: no login
    assert.equal(pic.status, 200);
    assert.equal(pic.headers.get('content-type'), 'image/jpeg');
    assert.match(pic.headers.get('cache-control'), /immutable/);
    assert.match(pic.headers.get('cache-control'), /max-age=31536000/);
    assert.equal(pic.headers.get('x-content-type-options'), 'nosniff');
    assert.match(pic.headers.get('content-security-policy'), /default-src 'none'/);
    assert.ok(pic.headers.get('etag'));
    assert.ok(Buffer.from(await pic.arrayBuffer()).equals(JPEG), 'the exact bytes come back');
    assert.equal((await fetch(srv.base + imageUrl, { method: 'HEAD' })).status, 200);
  } finally { await srv.stop(); }
});

test('replacing a picture gives a new address (so phones fetch it again), and the old address stops working', async () => {
  const srv = await startServer();
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    const first = (await (await putImage(srv, cookie, r.id, JPEG)).json()).imageUrl;
    const before = (await call(srv, 'GET', '/api/catalog?since=0')).body.recipes[0].updatedAt;
    await new Promise((x) => setTimeout(x, 5));
    const second = (await (await putImage(srv, cookie, r.id, PNG)).json()).imageUrl;
    assert.notEqual(first, second);
    assert.match(second, /\.png$/);
    assert.equal((await fetch(srv.base + first)).status, 404, 'the old picture is gone');
    assert.equal((await fetch(srv.base + second)).status, 200);
    assert.ok((await call(srv, 'GET', '/api/catalog?since=0')).body.recipes[0].updatedAt > before, 'the recipe counts as changed, so apps sync it');
    // the same bytes again: same address, nothing odd
    assert.equal((await (await putImage(srv, cookie, r.id, PNG)).json()).imageUrl, second);
  } finally { await srv.stop(); }
});

test('removing a picture empties imageUrl, marks the recipe as changed, and the picture is no longer served', async () => {
  const srv = await startServer();
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    const url = (await (await putImage(srv, cookie, r.id, WEBP)).json()).imageUrl;
    const t0 = (await call(srv, 'GET', '/api/catalog?since=0')).body.serverTime;
    await new Promise((x) => setTimeout(x, 5));
    const del = await fetch(`${srv.base}/admin/api/recipes/${r.id}/image`, { method: 'DELETE', headers: { cookie } });
    assert.equal(del.status, 200);
    assert.equal((await del.json()).imageUrl, null);
    assert.equal((await fetch(srv.base + url)).status, 404);
    const changed = (await call(srv, 'GET', `/api/catalog?since=${t0}`)).body.recipes;
    assert.equal(changed.length, 1, 'only the changed recipe comes in the next sync');
    assert.equal(changed[0].imageUrl, null);
    assert.equal((await fetch(`${srv.base}/admin/api/recipes/${r.id}/image`, { method: 'DELETE', headers: { cookie } })).status, 200, 'removing twice is harmless');
  } finally { await srv.stop(); }
});

test('only real JPEG, PNG and WebP files up to 2 MB are accepted', async () => {
  const srv = await startServer();
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    const status = async (body) => (await putImage(srv, cookie, r.id, body)).status;
    assert.equal(await status(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>')), 400);   // SVG can carry script
    assert.equal(await status(Buffer.from('GIF89a......pretend-gif')), 400);
    assert.equal(await status(Buffer.from('just some text, not a picture')), 400);
    assert.equal(await status(Buffer.from([0xff, 0xd8])), 400);                                     // too short to be a picture
    assert.equal(await status(Buffer.alloc(0)), 400);
    assert.equal(await status(Buffer.concat([JPEG, Buffer.alloc(2 * 1024 * 1024)])), 413);          // over 2 MB
    assert.equal((await call(srv, 'GET', '/api/catalog?since=0')).body.recipes[0].imageUrl, null, 'nothing was stored');
    const big = Buffer.concat([JPEG, Buffer.alloc(2 * 1024 * 1024 - JPEG.length)]);                 // exactly at the limit
    assert.equal(big.length, 2 * 1024 * 1024);
    assert.equal(await status(big), 200);
  } finally { await srv.stop(); }
});

test('the picture address cannot be guessed or reused: wrong hash, wrong recipe, wrong type and odd paths are all 404', async () => {
  const srv = await startServer();
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    const other = await newRecipe(srv, cookie, 'Друг рецепт');
    const url = (await (await putImage(srv, cookie, r.id, JPEG)).json()).imageUrl;
    const [, id, sha] = /^\/img\/(\d+)-([0-9a-f]{16})\.jpg$/.exec(url);
    const bad = [`/img/${id}-${'0'.repeat(16)}.jpg`, `/img/${other.id}-${sha}.jpg`, `/img/${id}-${sha}.png`, `/img/${id}-${sha}.svg`, `/img/${id}-${sha}.jpg/x`,
      `/img/${id}.jpg`, `/img/%2e%2e%2fadmin`, `/img/${id}-${sha.toUpperCase()}.jpg`, `/img/999999-${sha}.jpg`, '/img/'];
    for (const p of bad) assert.equal((await fetch(srv.base + p)).status, 404, p);
    assert.equal((await fetch(srv.base + url, { method: 'POST' })).status, 404);
  } finally { await srv.stop(); }
});

test('only a logged-in admin can set or remove a picture; a missing recipe is 404; deleting the recipe deletes its picture', async () => {
  const srv = await startServer();
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    assert.equal((await fetch(`${srv.base}/admin/api/recipes/${r.id}/image`, { method: 'PUT', body: JPEG })).status, 401);
    assert.equal((await fetch(`${srv.base}/admin/api/recipes/${r.id}/image`, { method: 'DELETE' })).status, 401);
    assert.equal((await putImage(srv, cookie, 99999, JPEG)).status, 404);
    const url = (await (await putImage(srv, cookie, r.id, JPEG)).json()).imageUrl;
    assert.equal((await fetch(`${srv.base}/admin/api/recipes/${r.id}`, { method: 'DELETE', headers: { cookie } })).status, 200);
    assert.equal((await fetch(srv.base + url)).status, 404);
    assert.equal((await putImage(srv, cookie, r.id, JPEG)).status, 404, 'a deleted recipe takes no new picture');
    const gone = (await call(srv, 'GET', '/api/catalog?since=0')).body.recipes.find((x) => x.id === r.id);
    assert.equal(gone.deleted, true);
    assert.equal(gone.imageUrl, null);
  } finally { await srv.stop(); }
});

test('with CATALOG_LOCKED=1 the picture is still public (it is part of what a Free user may see)', async () => {
  const srv = await startServer({ CATALOG_LOCKED: '1' });
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    const url = (await (await putImage(srv, cookie, r.id, JPEG)).json()).imageUrl;
    const c = (await call(srv, 'GET', '/api/catalog?since=0')).body.recipes[0];
    assert.equal(c.locked, true);
    assert.equal(c.imageUrl, url);
    assert.deepEqual(c.instructionSteps, []);
  } finally { await srv.stop(); }
});

test('the admin page may show recipe pictures and a preview of the chosen file, but still no outside content', async () => {
  const srv = await startServer();
  try {
    const res = await fetch(srv.base + '/admin');
    const csp = res.headers.get('content-security-policy');
    assert.match(csp, /img-src 'self' blob:/);
    assert.match(csp, /default-src 'self'/);
    assert.ok(!/http/.test(csp), 'no outside address is allowed');
  } finally { await srv.stop(); }
});

// ---------- where the picture is framed (the point that stays in view when the app crops it to a circle) ----------

const putFocus = (srv, cookie, id, body) => fetch(`${srv.base}/admin/api/recipes/${id}/image/focus`, { method: 'PUT', headers: { cookie, 'content-type': 'application/json' }, body: JSON.stringify(body) });
const catalogRecipe = async (srv, id) => (await call(srv, 'GET', '/api/catalog?since=0')).body.recipes.find((x) => x.id === id);

test('a new picture is framed in the middle unless the admin chose another point; without a picture there is no framing', async () => {
  const srv = await startServer();
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    assert.equal(r.imageFocusX, null);
    assert.equal(r.imageFocusY, null);

    const first = await (await putImage(srv, cookie, r.id, JPEG)).json();
    assert.equal(first.imageFocusX, 0.5);
    assert.equal(first.imageFocusY, 0.5);
    let c = await catalogRecipe(srv, r.id);
    assert.equal(c.imageFocusX, 0.5);
    assert.equal(c.imageFocusY, 0.5);

    const chosen = await fetch(`${srv.base}/admin/api/recipes/${r.id}/image?fx=0.25&fy=0.8`, { method: 'PUT', headers: { cookie }, body: PNG });
    assert.equal(chosen.status, 200);
    c = await catalogRecipe(srv, r.id);
    assert.equal(c.imageFocusX, 0.25);
    assert.equal(c.imageFocusY, 0.8);

    // a replacement picture without a point starts in the middle again (a different photo needs a different framing)
    await putImage(srv, cookie, r.id, JPEG);
    c = await catalogRecipe(srv, r.id);
    assert.equal(c.imageFocusX, 0.5);

    await fetch(`${srv.base}/admin/api/recipes/${r.id}/image`, { method: 'DELETE', headers: { cookie } });
    c = await catalogRecipe(srv, r.id);
    assert.equal(c.imageFocusX, null);
    assert.equal(c.imageFocusY, null);
  } finally { await srv.stop(); }
});

test('the framing can be moved without sending the picture again: the address stays and the recipe counts as changed', async () => {
  const srv = await startServer();
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    const { imageUrl } = await (await putImage(srv, cookie, r.id, JPEG)).json();
    const t0 = (await call(srv, 'GET', '/api/catalog?since=0')).body.serverTime;
    await new Promise((x) => setTimeout(x, 5));

    const res = await putFocus(srv, cookie, r.id, { x: 0.123456, y: 1 });
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.imageUrl, imageUrl, 'the picture itself (and so its cached copy on phones) is unchanged');
    assert.equal(body.imageFocusX, 0.123, 'rounded to three decimals');
    assert.equal(body.imageFocusY, 1);

    const changed = (await call(srv, 'GET', `/api/catalog?since=${t0}`)).body.recipes;
    assert.equal(changed.length, 1, 'phones pick up the new framing at their next sync');
    assert.equal(changed[0].imageFocusX, 0.123);
    assert.equal((await fetch(srv.base + imageUrl)).status, 200);

    assert.equal((await (await putFocus(srv, cookie, r.id, { x: 0, y: 0 })).json()).imageFocusX, 0, 'the edges are allowed');
    const middle = await (await putFocus(srv, cookie, r.id, {})).json();
    assert.equal(middle.imageFocusX, 0.5, 'nothing given means the middle');
  } finally { await srv.stop(); }
});

test('a framing point outside the picture, or not a number, is refused and changes nothing', async () => {
  const srv = await startServer();
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    await fetch(`${srv.base}/admin/api/recipes/${r.id}/image?fx=0.3&fy=0.4`, { method: 'PUT', headers: { cookie }, body: JPEG });
    for (const bad of [{ x: -0.1, y: 0.5 }, { x: 0.5, y: 1.0001 }, { x: 2, y: 0.5 }, { x: 'abc', y: 0.5 }, { x: [0.2], y: 0.5 }, { x: true, y: 0.5 }, { x: {}, y: 0.5 }, { x: 0.5, y: 'NaN' }, { x: 0.5, y: 1e9 }]) {
      assert.equal((await putFocus(srv, cookie, r.id, bad)).status, 400, JSON.stringify(bad));
    }
    for (const q of ['fx=-1', 'fx=1.5', 'fy=abc', 'fx=0.5&fy=7']) {
      assert.equal((await fetch(`${srv.base}/admin/api/recipes/${r.id}/image?${q}`, { method: 'PUT', headers: { cookie }, body: PNG })).status, 400, q);
    }
    const c = await catalogRecipe(srv, r.id);
    assert.equal(c.imageFocusX, 0.3);
    assert.equal(c.imageFocusY, 0.4);
    assert.match(c.imageUrl, /\.jpg$/, 'the picture was not replaced by a refused request either');
  } finally { await srv.stop(); }
});

test('moving the framing needs the admin login, a recipe, and a picture', async () => {
  const srv = await startServer();
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    const noLogin = await fetch(`${srv.base}/admin/api/recipes/${r.id}/image/focus`, { method: 'PUT', headers: { 'content-type': 'application/json' }, body: '{"x":0.1,"y":0.1}' });
    assert.equal(noLogin.status, 401);
    assert.equal((await putFocus(srv, cookie, r.id, { x: 0.1, y: 0.1 })).status, 404, 'a recipe without a picture has nothing to frame');
    assert.equal((await putFocus(srv, cookie, 99999, { x: 0.1, y: 0.1 })).status, 404);
    await putImage(srv, cookie, r.id, JPEG);
    assert.equal((await fetch(`${srv.base}/admin/api/recipes/${r.id}/image/focus`, { method: 'POST', headers: { cookie } })).status, 404, 'only PUT');
    assert.equal((await fetch(`${srv.base}/admin/api/recipes/${r.id}/image/other`, { method: 'PUT', headers: { cookie }, body: JPEG })).status, 404);
  } finally { await srv.stop(); }
});

test('a server that already holds pictures from before the framing existed is upgraded when it starts', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rucek-upgrade-'));
  const dbFile = path.join(dir, 'old.db');
  const { DatabaseSync } = require('node:sqlite');
  const old = new DatabaseSync(dbFile);
  // exactly the table as the previous version created it: no framing columns
  old.exec(`CREATE TABLE recipe_images (recipe_id INTEGER PRIMARY KEY, mime TEXT NOT NULL, sha TEXT NOT NULL, size INTEGER NOT NULL, bytes BLOB NOT NULL, updated_at INTEGER NOT NULL);`);
  old.close();
  const srv = await startServer({ DB_PATH: dbFile });
  try {
    const cookie = await adminCookie(srv);
    const r = await newRecipe(srv, cookie);
    const put = await fetch(`${srv.base}/admin/api/recipes/${r.id}/image?fx=0.2&fy=0.9`, { method: 'PUT', headers: { cookie }, body: JPEG });
    assert.equal(put.status, 200);
    const c = await catalogRecipe(srv, r.id);
    assert.equal(c.imageFocusX, 0.2);
    assert.equal(c.imageFocusY, 0.9);
  } finally { await srv.stop(); fs.rmSync(dir, { recursive: true, force: true }); }
});
