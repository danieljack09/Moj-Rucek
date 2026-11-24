'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const { createUserStore } = require('../users');
const { createLimits, lockedView, MAX_RECIPES_FREE, MAX_RECIPES_PAID } = require('../limits');

const HOUR = 3600 * 1000;
const DAY = 24 * HOUR;
const RECIPES = new Map();
for (let id = 1; id <= 60; id++) {
  RECIPES.set(id, {
    id, name: `Рецепт ${id}`, description: `опис ${id}`, instructionSteps: [`чекор ${id}`], notes: 'забелешка', tags: ['т'],
    ingredients: [{ name: 'Јајца', quantity: 2, unit: 'PIECE' }], deleted: false,
  });
}
const recipes = {
  getMany: (ids) => ids.map((i) => RECIPES.get(i)).filter(Boolean),
  all: () => [...RECIPES.values()],
};

function setup() {
  const clock = { t: Date.UTC(2026, 10, 24, 10, 0, 0) };
  const db = new DatabaseSync(':memory:');
  db.exec('PRAGMA foreign_keys = ON;');
  const users = createUserStore(db, { now: () => clock.t });
  const limits = createLimits(db, users, recipes, { now: () => clock.t });
  const user = (uid, provider = 'google.com') => users.upsertSession({ uid, provider });
  const fresh = (uid) => users.findByUid(uid);
  let n = 0;
  const rid = () => `req-${String(++n).padStart(8, '0')}`;
  return { clock, db, users, limits, user, fresh, rid };
}

const code = (fn) => { try { fn(); } catch (e) { return { code: e.extra?.code, status: e.status, extra: e.extra }; } return null; };

// ---------- Free and Guest ----------

test('a Free user makes a list with 1 to 3 recipes and gets their full details, unlocked for 72 hours', () => {
  const { clock, limits, user, rid } = setup();
  const u = user('f1');
  const r = limits.generate(u, [3, 1, 2], rid());

  assert.equal(r.createdAt, clock.t);
  assert.equal(r.unlockedUntil, clock.t + 72 * HOUR);
  assert.equal(r.nextAllowedAt, clock.t + 72 * HOUR);
  assert.deepEqual(r.recipes.map((x) => x.id), [1, 2, 3]);
  assert.deepEqual(r.recipes[0].instructionSteps, ['чекор 1']);   // the locked fields are there now
  assert.equal(r.serverTime, clock.t);
});

test('the same rules apply to a Guest from day one', () => {
  const { limits, user, rid } = setup();
  const g = user('g1', 'anonymous');
  assert.equal(g.plan, 'GUEST');
  limits.generate(g, [1, 2, 3], rid());
  assert.equal(code(() => limits.generate(g, [4], rid())).code, 'TOO_SOON');
});

test('a second list within 72 hours is refused with the exact time it becomes possible', () => {
  const { clock, limits, user, rid } = setup();
  const u = user('f1');
  const first = limits.generate(u, [1], rid());
  clock.t += 71 * HOUR + 59 * 60 * 1000;
  const e = code(() => limits.generate(u, [2], rid()));
  assert.equal(e.code, 'TOO_SOON');
  assert.equal(e.status, 409);
  assert.equal(e.extra.nextAllowedAt, first.createdAt + 72 * HOUR);
});

test('the 72 hours end exactly 72 hours after the successful list, not at midnight', () => {
  const { clock, limits, user, rid } = setup();
  const u = user('f1');
  limits.generate(u, [1], rid());                       // Tuesday 10:00
  clock.t += 72 * HOUR - 1;
  assert.equal(code(() => limits.generate(u, [2], rid())).code, 'TOO_SOON');
  clock.t += 1;                                          // Friday 10:00 on the dot
  assert.ok(limits.generate(u, [2], rid()));
});

test('a refused attempt never starts the clock', () => {
  const { limits, user, rid } = setup();
  const u = user('f1');
  assert.equal(code(() => limits.generate(u, [1, 2, 3, 4], rid())).code, 'TOO_MANY_RECIPES');
  assert.equal(code(() => limits.generate(u, [999], rid())).code, 'UNKNOWN_RECIPE');
  assert.ok(limits.generate(u, [1], rid()));             // still allowed right away
});

test('the number and kind of recipes is checked', () => {
  const { limits, user, rid } = setup();
  const u = user('f1');
  assert.equal(MAX_RECIPES_FREE, 3);
  assert.equal(code(() => limits.generate(u, [], rid())).code, 'NO_RECIPES');
  assert.equal(code(() => limits.generate(u, 'x', rid())).code, 'NO_RECIPES');
  assert.equal(code(() => limits.generate(u, [1, 1], rid())).code, 'BAD_RECIPES');
  assert.equal(code(() => limits.generate(u, [0], rid())).code, 'BAD_RECIPES');
  assert.equal(code(() => limits.generate(u, [-2], rid())).code, 'BAD_RECIPES');
  assert.equal(code(() => limits.generate(u, [1.5], rid())).code, 'BAD_RECIPES');
  assert.equal(code(() => limits.generate(u, ['a'], rid())).code, 'BAD_RECIPES');
  assert.equal(code(() => limits.generate(u, [1, 2, 3, 4], rid())).extra.max, 3);
});

test('a deleted recipe cannot be chosen', () => {
  const { limits, user, rid } = setup();
  const saved = RECIPES.get(7);
  RECIPES.delete(7);
  try { assert.equal(code(() => limits.generate(user('f1'), [7], rid())).code, 'UNKNOWN_RECIPE'); } finally { RECIPES.set(7, saved); }
});

test('the request id must look like an id', () => {
  const { limits, user } = setup();
  const u = user('f1');
  for (const bad of [undefined, null, '', 'short', 'has space in it', 'x'.repeat(65), 12345678, {}]) {
    assert.equal(code(() => limits.generate(u, [1], bad)).code, 'BAD_REQUEST_ID', String(bad));
  }
});

// ---------- safe retry ----------

test('asking again with the same request id gives the same answer and uses up nothing more', () => {
  const { clock, db, limits, user } = setup();
  const u = user('f1');
  const first = limits.generate(u, [1, 2], 'request-AAAA');
  clock.t += 30 * 60 * 1000;                             // the retry arrives half an hour later
  const second = limits.generate(u, [2, 1], 'request-AAAA');

  assert.equal(second.generationId, first.generationId);
  assert.equal(second.createdAt, first.createdAt);
  assert.equal(second.unlockedUntil, first.unlockedUntil);
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM generations').get().n, 1);
});

test('the same request id with different recipes is refused', () => {
  const { limits, user } = setup();
  const u = user('f1');
  limits.generate(u, [1, 2], 'request-AAAA');
  assert.equal(code(() => limits.generate(u, [3], 'request-AAAA')).code, 'REQUEST_REUSED');
});

test('a request id of one user means nothing to another', () => {
  const { limits, user } = setup();
  limits.generate(user('a'), [1], 'request-AAAA');
  assert.ok(limits.generate(user('b'), [2], 'request-AAAA'));
});

test('two different requests right after each other: only the first wins', () => {
  const { limits, user, rid } = setup();
  const u = user('f1');
  limits.generate(u, [1], rid());
  assert.equal(code(() => limits.generate(u, [2], rid())).code, 'TOO_SOON');
});

// ---------- the guest's 14 days ----------

test('a guest whose 14 days are over cannot make a new list, but can use a retry of an earlier one', () => {
  const { clock, limits, user, fresh, rid } = setup();
  user('g1', 'anonymous');
  limits.generate(fresh('g1'), [1], 'request-AAAA');
  clock.t += 14 * DAY;                                   // exactly the end
  const e = code(() => limits.generate(fresh('g1'), [2], rid()));
  assert.equal(e.code, 'GUEST_EXPIRED');
  assert.equal(e.status, 403);
  assert.ok(limits.generate(fresh('g1'), [1], 'request-AAAA'), 'an answer lost before the end can still be fetched');
});

test('one moment before the end a guest can still make a list', () => {
  const { clock, limits, user, fresh, rid } = setup();
  user('g1', 'anonymous');
  clock.t += 14 * DAY - 1;
  assert.ok(limits.generate(fresh('g1'), [1], rid()));
});

test('a guest who links Google becomes Free: allowed again, and the running 72 hours keep running', () => {
  const { clock, limits, users, user, fresh, rid } = setup();
  user('g1', 'anonymous');
  limits.generate(fresh('g1'), [1], rid());
  clock.t += 10 * HOUR;
  users.upsertSession({ uid: 'g1', provider: 'google.com' });      // linking keeps the same user
  const linked = fresh('g1');
  assert.equal(linked.kind, 'REGISTERED');
  assert.equal(code(() => limits.generate(linked, [2], rid())).code, 'TOO_SOON');
  clock.t += 62 * HOUR;
  assert.ok(limits.generate(linked, [2], rid()));
});

test('an expired guest who links Google can make a list at once (if 72 hours have passed)', () => {
  const { clock, limits, users, user, fresh, rid } = setup();
  user('g1', 'anonymous');
  clock.t += 15 * DAY;
  assert.equal(code(() => limits.generate(fresh('g1'), [1], rid())).code, 'GUEST_EXPIRED');
  users.upsertSession({ uid: 'g1', provider: 'google.com' });
  assert.ok(limits.generate(fresh('g1'), [1], rid()));
});

// ---------- Paid ----------

test('Paid: no wait between lists, up to 50 recipes, no unlock deadline', () => {
  const { limits, users, user, fresh, rid } = setup();
  const u = user('p1');
  assert.equal(users.setPlan(u.id, 'PAID'), true);
  const paid = fresh('p1');
  const a = limits.generate(paid, Array.from({ length: 50 }, (_, i) => i + 1), rid());
  assert.equal(a.recipes.length, 50);
  assert.equal(a.unlockedUntil, null);
  assert.equal(a.nextAllowedAt, null);
  assert.ok(limits.generate(paid, [1], rid()), 'no 72 hour wait');
  assert.equal(MAX_RECIPES_PAID, 50);
  assert.equal(code(() => limits.generate(paid, Array.from({ length: 51 }, (_, i) => i + 1), rid())).code, 'TOO_MANY_RECIPES');
});

test('when Paid ends the user is Free again: the last list still counts for the 72 hours', () => {
  const { clock, limits, users, user, fresh, rid } = setup();
  const u = user('p1');
  users.setPlan(u.id, 'PAID');
  limits.generate(fresh('p1'), [1], rid());
  users.setPlan(u.id, 'FREE');
  assert.equal(code(() => limits.generate(fresh('p1'), [2], rid())).code, 'TOO_SOON');
  clock.t += 72 * HOUR;
  assert.ok(limits.generate(fresh('p1'), [2], rid()));
});

test('only registered users can be set to Paid or Free; a guest never changes; only those two values are accepted', () => {
  const { users, user } = setup();
  const g = user('g1', 'anonymous');
  const f = user('f1');
  assert.equal(users.setPlan(g.id, 'PAID'), false);
  assert.equal(users.findByUid('g1').plan, 'GUEST');
  assert.equal(users.setPlan('usr_unknown', 'PAID'), false);
  assert.throws(() => users.setPlan(f.id, 'GUEST'));
  assert.throws(() => users.setPlan(f.id, 'ADMIN'));
  assert.equal(users.findByUid('f1').plan, 'FREE');
});

// ---------- state ----------

test('state: before any list everything is possible and nothing is unlocked', () => {
  const { limits, user, clock } = setup();
  const s = limits.state(user('f1'));
  assert.equal(s.plan, 'FREE');
  assert.equal(s.canGenerateNow, true);
  assert.equal(s.nextAllowedAt, null);
  assert.deepEqual(s.recipes, []);
  assert.equal(s.serverTime, clock.t);
});

test('state: after a list its recipes are unlocked and the next list is timed; after 72 hours the details lock again', () => {
  const { clock, limits, user, rid } = setup();
  const u = user('f1');
  const gen = limits.generate(u, [5, 6], rid());
  let s = limits.state(u);
  assert.equal(s.canGenerateNow, false);
  assert.equal(s.nextAllowedAt, gen.createdAt + 72 * HOUR);
  assert.equal(s.unlockedUntil, gen.createdAt + 72 * HOUR);
  assert.deepEqual(s.recipes.map((r) => r.id), [5, 6]);

  clock.t += 72 * HOUR;
  s = limits.state(u);
  assert.equal(s.canGenerateNow, true);
  assert.deepEqual(s.recipes, [], 'locked again');
  assert.equal(s.unlockedUntil, null);
});

test('state: an expired guest cannot generate; a Paid user sees everything', () => {
  const { clock, limits, users, user, fresh } = setup();
  user('g1', 'anonymous');
  clock.t += 20 * DAY;
  const s = limits.state(fresh('g1'));
  assert.equal(s.guestExpired, true);
  assert.equal(s.canGenerateNow, false);

  const p = user('p1');
  users.setPlan(p.id, 'PAID');
  const ps = limits.state(fresh('p1'));
  assert.equal(ps.canGenerateNow, true);
  assert.equal(ps.recipes.length, 60);
  assert.equal(ps.unlockedUntil, null);
});

test('users do not affect each other', () => {
  const { limits, user, rid } = setup();
  limits.generate(user('a'), [1], rid());
  assert.ok(limits.generate(user('b'), [1], rid()));
  assert.deepEqual(limits.state(user('c')).recipes, []);
});

// ---------- deleting an account ----------

test('deleting a user also deletes their lists, and nobody else\'s', () => {
  const { db, limits, users, user, rid } = setup();
  limits.generate(user('a'), [1, 2], rid());
  limits.generate(user('b'), [3], rid());

  users.deleteByUid('a');

  const left = db.prepare('SELECT user_id FROM generations').all();
  assert.equal(left.length, 1);
  assert.equal(left[0].user_id, users.findByUid('b').id);
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM generation_recipes').get().n, 1);
  // signing in again is a brand-new user with a clean slate
  assert.ok(limits.generate(user('a'), [1], rid()));
});

// ---------- the locked view of a recipe ----------

test('the public view keeps what is needed to choose and shop, and empties the rest', () => {
  const v = lockedView(RECIPES.get(1));
  assert.equal(v.locked, true);
  assert.equal(v.description, null);
  assert.deepEqual(v.instructionSteps, []);
  assert.equal(v.notes, null);
  assert.deepEqual(v.tags, []);
  assert.equal(v.name, 'Рецепт 1');
  assert.deepEqual(v.ingredients, RECIPES.get(1).ingredients);
  assert.equal(RECIPES.get(1).description, 'опис 1', 'the original is not changed');
});
