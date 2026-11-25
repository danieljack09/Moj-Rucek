'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const store = require('./db');
const { HttpError } = require('./lib/http-error');
const { createGoogleCertKeyCache, createJwksKeyCache } = require('./lib/jwt');
const { createFirebaseIdTokenVerifier } = require('./lib/firebase-auth');
const { createAppCheckVerifier } = require('./lib/app-check');
const { createUserStore } = require('./users');
const { createLimits } = require('./limits');
const { createSync, createSyncApi } = require('./sync');
const { createIdentity, clientAddress } = require('./identity');
const { INGREDIENT_CATEGORIES, RECIPE_CATEGORIES, UNITS } = require('./constants');

const PORT = Number(process.env.PORT || 3100);
const HOST = process.env.HOST || '127.0.0.1';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;
if (!ADMIN_PASSWORD || ADMIN_PASSWORD.length < 10) {
  console.error('ADMIN_PASSWORD must be set (at least 10 characters).');
  process.exit(1);
}
const SECRET = process.env.SESSION_SECRET || crypto.randomBytes(32).toString('hex');
const SESSION_TTL_MS = 12 * 60 * 60 * 1000;
const COOKIE = 'rucek_admin';

const ADMIN_HTML = fs.readFileSync(path.join(__dirname, 'public', 'admin.html'));
// Public page required by Google Play: how to delete an account and its data (in the app, or by email).
const DELETE_ACCOUNT_HTML = fs.readFileSync(path.join(__dirname, 'public', 'delete-account.html'));
// Public privacy policy (Macedonian), also required by Google Play.
const PRIVACY_HTML = fs.readFileSync(path.join(__dirname, 'public', 'privacy.html'));
const PUBLIC_PAGE_HEADERS = {
  // Plain text pages: no scripts, nothing embedded, not framed.
  'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'",
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer',
  'Cache-Control': 'public, max-age=300',
};

// ---------- accounts (Firebase identity) ----------
// Off until FIREBASE_PROJECT_ID is set: /api/session and /api/me then answer 503, everything else works as before.
//   FIREBASE_PROJECT_ID      e.g. the Firebase project id (needed to verify sign-in tokens)
//   FIREBASE_PROJECT_NUMBER  needed for App Check only
//   APPCHECK_MODE            off | monitor | enforce   (default: monitor if the project number is set, else off)
//   ALLOWED_PROVIDERS        comma list, default "anonymous,google.com,password" (an unverified password account counts as a guest)
const FIREBASE_PROJECT_ID = (process.env.FIREBASE_PROJECT_ID || '').trim();
const FIREBASE_PROJECT_NUMBER = (process.env.FIREBASE_PROJECT_NUMBER || '').trim();
let APPCHECK_MODE = (process.env.APPCHECK_MODE || (FIREBASE_PROJECT_NUMBER ? 'monitor' : 'off')).trim();
if (!['off', 'monitor', 'enforce'].includes(APPCHECK_MODE)) {
  console.error(`APPCHECK_MODE must be off, monitor or enforce (got "${APPCHECK_MODE}"); using off.`);
  APPCHECK_MODE = 'off';
}
if (APPCHECK_MODE !== 'off' && !FIREBASE_PROJECT_NUMBER) {
  console.error('APPCHECK_MODE needs FIREBASE_PROJECT_NUMBER; App Check stays off.');
  APPCHECK_MODE = 'off';
}
const ALLOWED_PROVIDERS = (process.env.ALLOWED_PROVIDERS || 'anonymous,google.com,password').split(',').map((v) => v.trim()).filter(Boolean);

const users = createUserStore(store.db);
const sync = createSync(store.db, users);
const syncApi = createSyncApi(sync, users);
const limits = createLimits(store.db, users, { getMany: store.getRecipesByIds, all: store.allRecipesFull });

// CATALOG_LOCKED=1: the public catalog stops sending description, steps, notes and tags (they come only with an
// approved list, see /api/generations). Off by default until the app version that understands it is out.
const CATALOG_LOCKED = process.env.CATALOG_LOCKED === '1';
const identity = createIdentity({
  idTokenVerifier: FIREBASE_PROJECT_ID
    ? createFirebaseIdTokenVerifier({
        projectId: FIREBASE_PROJECT_ID,
        getKey: createGoogleCertKeyCache().getKey,
        allowedProviders: ALLOWED_PROVIDERS,
      })
    : null,
  appCheckVerifier: APPCHECK_MODE !== 'off'
    ? createAppCheckVerifier({ projectNumber: FIREBASE_PROJECT_NUMBER, getKey: createJwksKeyCache().getKey })
    : null,
  appCheckMode: APPCHECK_MODE,
});

// ---------- auth ----------

const sha = (s) => crypto.createHash('sha256').update(s).digest();
const hmac = (s) => crypto.createHmac('sha256', SECRET).update(s).digest('base64url');

function passwordOk(candidate) {
  return crypto.timingSafeEqual(sha(String(candidate)), sha(ADMIN_PASSWORD));
}

function makeToken() {
  const exp = String(Date.now() + SESSION_TTL_MS);
  return `${exp}.${hmac(exp)}`;
}

function tokenOk(token) {
  if (!token) return false;
  const [exp, sig] = token.split('.');
  if (!exp || !sig) return false;
  const expected = hmac(exp);
  if (sig.length !== expected.length || !crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) return false;
  return Number(exp) > Date.now();
}

function readCookie(req, name) {
  for (const part of (req.headers.cookie || '').split(';')) {
    const i = part.indexOf('=');
    if (i > 0 && part.slice(0, i).trim() === name) return part.slice(i + 1).trim();
  }
  return null;
}

const isAuthed = (req) => tokenOk(readCookie(req, COOKIE));

// 5 failed logins per 15 minutes per client address.
const attempts = new Map();
const clientIp = clientAddress;
function loginBlocked(ip) {
  const a = attempts.get(ip);
  if (!a || a.resetAt < Date.now()) return false;
  return a.count >= 5;
}
function loginFailed(ip) {
  const a = attempts.get(ip);
  if (!a || a.resetAt < Date.now()) attempts.set(ip, { count: 1, resetAt: Date.now() + 15 * 60 * 1000 });
  else a.count++;
}

// ---------- http helpers ----------

function send(res, status, body, headers = {}) {
  const isJson = typeof body === 'object' && !Buffer.isBuffer(body);
  const payload = isJson ? JSON.stringify(body) : body;
  res.writeHead(status, {
    'Content-Type': isJson ? 'application/json; charset=utf-8' : 'text/html; charset=utf-8',
    'X-Content-Type-Options': 'nosniff',
    'Cache-Control': 'no-store',
    ...headers,
  });
  res.end(payload);
}

function readJson(req, maxBytes = 1_000_000) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > maxBytes) {
        reject(new HttpError(413, 'Преголемо барање.'));
        req.destroy();
      } else chunks.push(c);
    });
    req.on('end', () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}'));
      } catch {
        reject(new HttpError(400, 'Невалиден JSON.'));
      }
    });
    req.on('error', reject);
  });
}

// ---------- validation ----------

const bad = (msg) => new HttpError(400, msg);
const str = (v, max) => (typeof v === 'string' ? v.trim().slice(0, max) : '');
const optStr = (v, max) => str(v, max) || null;
const int = (v, min, max, label) => {
  const n = Number(v);
  if (!Number.isInteger(n) || n < min || n > max) throw bad(`${label}: невалидна вредност.`);
  return n;
};

function validateIngredient(b) {
  const name = str(b.name, 100);
  if (!name) throw bad('Името е задолжително.');
  if (!(b.category in INGREDIENT_CATEGORIES)) throw bad('Невалидна категорија.');
  if (!(b.defaultUnit in UNITS)) throw bad('Невалидна единица.');
  return { name, category: b.category, defaultUnit: b.defaultUnit };
}

function validateRecipe(b) {
  const name = str(b.name, 150);
  if (!name) throw bad('Името е задолжително.');
  if (b.category != null && b.category !== '' && !(b.category in RECIPE_CATEGORIES)) throw bad('Невалидна категорија.');
  const lines = Array.isArray(b.ingredients) ? b.ingredients : [];
  if (lines.length === 0) throw bad('Додади барем една состојка.');
  if (lines.length > 100) throw bad('Премногу состојки.');
  const ingredients = lines.map((l, i) => {
    const lname = str(l.name, 100);
    const qty = Number(l.quantity);
    if (!lname) throw bad(`Состојка ${i + 1}: недостасува име.`);
    if (!Number.isFinite(qty) || qty < 0 || qty > 1_000_000) throw bad(`Состојка ${i + 1}: невалидна количина.`);
    if (!(l.unit in UNITS)) throw bad(`Состојка ${i + 1}: невалидна единица.`);
    return { name: lname, quantity: qty, unit: l.unit, notes: optStr(l.notes, 200) };
  });
  const list = (v, max, maxLen) =>
    (Array.isArray(v) ? v : []).map((s) => str(s, maxLen)).filter(Boolean).slice(0, max);
  return {
    name,
    description: optStr(b.description, 1000),
    servings: int(b.servings ?? 3, 1, 100, 'Порции'),
    prepTimeMinutes: int(b.prepTimeMinutes ?? 0, 0, 10000, 'Време за подготовка'),
    cookTimeMinutes: int(b.cookTimeMinutes ?? 0, 0, 10000, 'Време за готвење'),
    category: b.category || null,
    tags: list(b.tags, 20, 40),
    instructionSteps: list(b.instructionSteps, 60, 1000),
    notes: optStr(b.notes, 1000),
    ingredients,
  };
}

// ---------- routing ----------

async function handle(req, res) {
  const url = new URL(req.url, 'http://localhost');
  const p = url.pathname.replace(/\/+$/, '') || '/';
  const m = req.method;

  if (p === '/health') return send(res, 200, { ok: true });

  if (p === '/delete-account' && (m === 'GET' || m === 'HEAD')) return send(res, 200, DELETE_ACCOUNT_HTML, PUBLIC_PAGE_HEADERS);
  if (p === '/privacy' && (m === 'GET' || m === 'HEAD')) return send(res, 200, PRIVACY_HTML, PUBLIC_PAGE_HEADERS);

  // Public, read-only: what the Android app syncs.
  if (p === '/api/catalog' && m === 'GET') {
    const since = Math.max(0, Number(url.searchParams.get('since')) || 0);
    return send(res, 200, store.catalogSince(since, { locked: CATALOG_LOCKED }));
  }

  // The app's account endpoints. Authorization: Bearer <Firebase ID token>.
  if (p === '/api/session' && m === 'POST') {
    const who = await identity.authenticate(req, clientAddress(req));
    return send(res, 200, users.publicShape(users.upsertSession(who)));
  }
  if (p === '/api/me' && m === 'GET') {
    const who = await identity.authenticate(req, clientAddress(req));
    const row = users.findByUid(who.uid);
    if (!row) throw new HttpError(404, 'Нема отворена сесија. Повикај /api/session.');
    return send(res, 200, users.publicShape(row));
  }

  // Making a shopping list: the server decides (Guest/Free limits, 72 h, Paid). Safe to repeat with the same requestId.
  if (p === '/api/generations' && m === 'POST') {
    const who = await identity.authenticate(req, clientAddress(req));
    const body = await readJson(req);
    const user = users.upsertSession(who);
    return send(res, 200, limits.generate(user, body.recipeIds, body.requestId));
  }

  // Where the user stands, and the details unlocked right now.
  if (p === '/api/unlocks' && m === 'GET') {
    const who = await identity.authenticate(req, clientAddress(req));
    return send(res, 200, limits.state(users.upsertSession(who)));
  }

  // Sync: one snapshot per account (see docs/sync-plan.md). The phone sends the revision it was based on.
  if (syncApi.handles(m, p)) {
    const who = await identity.authenticate(req, clientAddress(req));
    const r = await syncApi.handle(m, p, who, () => readJson(req, 5 * 1024 * 1024));
    return send(res, r.status, r.body);
  }

  // Account deletion: removes the caller's own record only. Already deleted (or never opened) is also 204.
  if (p === '/api/me' && m === 'DELETE') {
    const who = await identity.authenticate(req, clientAddress(req));
    users.deleteByUid(who.uid);
    res.writeHead(204, { 'Cache-Control': 'no-store' });
    return res.end();
  }

  if (p === '/admin' && m === 'GET') {
    return send(res, 200, ADMIN_HTML, {
      'Content-Security-Policy': "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'",
      'X-Frame-Options': 'DENY',
      'Referrer-Policy': 'no-referrer',
    });
  }

  if (p === '/admin/api/login' && m === 'POST') {
    const ip = clientIp(req);
    if (loginBlocked(ip)) throw new HttpError(429, 'Премногу обиди. Обиди се повторно подоцна.');
    const body = await readJson(req);
    if (!passwordOk(body.password ?? '')) {
      loginFailed(ip);
      throw new HttpError(401, 'Погрешна лозинка.');
    }
    const secure = req.headers['x-forwarded-proto'] === 'https' ? '; Secure' : '';
    return send(res, 200, { ok: true }, {
      'Set-Cookie': `${COOKIE}=${makeToken()}; HttpOnly; SameSite=Strict; Path=/admin; Max-Age=${SESSION_TTL_MS / 1000}${secure}`,
    });
  }

  if (p === '/admin/api/logout' && m === 'POST') {
    return send(res, 200, { ok: true }, { 'Set-Cookie': `${COOKIE}=; HttpOnly; SameSite=Strict; Path=/admin; Max-Age=0` });
  }

  if (p.startsWith('/admin/api/')) {
    if (!isAuthed(req)) throw new HttpError(401, 'Не си најавен.');
    const parts = p.slice('/admin/api/'.length).split('/');
    const [resource, idRaw] = parts;
    // Plan changes by hand (until billing exists): PUT /admin/api/users/plan {userId, plan}
    if (resource === 'users' && idRaw === 'plan' && m === 'PUT') {
      const body = await readJson(req);
      if (!users.setPlan(String(body.userId ?? ''), body.plan)) throw new HttpError(404, 'Корисникот не е пронајден или не е регистриран.');
      return send(res, 200, { ok: true });
    }

    const id = idRaw === undefined ? null : Number(idRaw);
    if (idRaw !== undefined && !Number.isInteger(id)) throw new HttpError(404, 'Не е пронајдено.');

    if (resource === 'meta' && m === 'GET') {
      return send(res, 200, { ingredientCategories: INGREDIENT_CATEGORIES, recipeCategories: RECIPE_CATEGORIES, units: UNITS });
    }

    if (resource === 'ingredients') {
      if (id === null && m === 'GET') return send(res, 200, store.listIngredients());
      if (id === null && m === 'POST') return send(res, 201, store.saveIngredient(null, validateIngredient(await readJson(req))));
      if (id !== null && m === 'PUT') return send(res, 200, store.saveIngredient(id, validateIngredient(await readJson(req))));
      if (id !== null && m === 'DELETE') { store.deleteIngredient(id); return send(res, 200, { ok: true }); }
    }

    if (resource === 'recipes') {
      if (id === null && m === 'GET') return send(res, 200, store.listRecipes());
      if (id === null && m === 'POST') return send(res, 201, store.saveRecipe(null, validateRecipe(await readJson(req))));
      if (id !== null && m === 'GET') return send(res, 200, store.getRecipe(id));
      if (id !== null && m === 'PUT') return send(res, 200, store.saveRecipe(id, validateRecipe(await readJson(req))));
      if (id !== null && m === 'DELETE') { store.deleteRecipe(id); return send(res, 200, { ok: true }); }
    }
  }

  throw new HttpError(404, 'Не е пронајдено.');
}

const server = http.createServer((req, res) => {
  handle(req, res).catch((err) => {
    if (err instanceof HttpError) return send(res, err.status, { error: err.message, ...err.extra });
    console.error(err);
    send(res, 500, { error: 'Грешка на серверот.' });
  });
});

server.listen(PORT, HOST, () => {
  console.log(`Moj Rucek catalog server on http://${HOST}:${PORT}`);
  console.log(identity.configured
    ? `Accounts: on (project ${FIREBASE_PROJECT_ID}), providers: ${ALLOWED_PROVIDERS.join(', ')}, App Check: ${APPCHECK_MODE}`
    : 'Accounts: off (set FIREBASE_PROJECT_ID to enable /api/session and /api/me)');
});
