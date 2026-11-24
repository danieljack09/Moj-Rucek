'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const { createUserStore, GUEST_DAYS } = require('../users');

const DAY = 24 * 3600 * 1000;
function setup() {
  const clock = { t: Date.UTC(2026, 10, 22, 12, 0, 0) };
  const db = new DatabaseSync(':memory:');
  const store = createUserStore(db, { now: () => clock.t });
  return { clock, db, store };
}

test('a new anonymous sign-in creates a guest with 14 days from the first server call', () => {
  const { clock, store } = setup();
  const started = clock.t;
  const u = store.upsertSession({ uid: 'u1', provider: 'anonymous' });
  assert.match(u.id, /^usr_[A-Za-z0-9_-]{20,}$/);
  assert.equal(u.kind, 'GUEST');
  assert.equal(u.plan, 'GUEST');
  assert.equal(u.guest_started_at, started);
  assert.equal(u.guest_expires_at, started + GUEST_DAYS * DAY);
  assert.equal(GUEST_DAYS, 14);
});

test('calling again is idempotent: same user, countdown does not restart', () => {
  const { clock, store } = setup();
  const first = store.upsertSession({ uid: 'u1', provider: 'anonymous' });
  clock.t += 5 * DAY;
  const again = store.upsertSession({ uid: 'u1', provider: 'anonymous' });
  assert.equal(again.id, first.id);
  assert.equal(again.guest_started_at, first.guest_started_at);
  assert.equal(again.guest_expires_at, first.guest_expires_at);
  assert.equal(again.last_seen_at, clock.t);
});

test('a new google.com user is registered FREE with no expiry', () => {
  const { store } = setup();
  const u = store.upsertSession({ uid: 'g1', provider: 'google.com' });
  assert.equal(u.kind, 'REGISTERED');
  assert.equal(u.plan, 'FREE');
  assert.equal(u.guest_expires_at, null);
  assert.equal(u.guest_started_at, null);
});

test('linking Google to a guest upgrades the SAME user: same id, expiry removed', () => {
  const { clock, store } = setup();
  const guest = store.upsertSession({ uid: 'u1', provider: 'anonymous' });
  clock.t += 3 * DAY;
  const linked = store.upsertSession({ uid: 'u1', provider: 'google.com' });
  assert.equal(linked.id, guest.id);
  assert.equal(linked.kind, 'REGISTERED');
  assert.equal(linked.plan, 'FREE');
  assert.equal(linked.guest_expires_at, null);
  assert.equal(linked.guest_started_at, guest.guest_started_at); // history kept
  assert.equal(linked.linked_at, clock.t);
  assert.equal(linked.sign_in_provider, 'google.com');
});

test('a registered user is never downgraded or given an expiry', () => {
  const { store } = setup();
  const reg = store.upsertSession({ uid: 'g1', provider: 'google.com' });
  const odd = store.upsertSession({ uid: 'g1', provider: 'anonymous' });
  assert.equal(odd.id, reg.id);
  assert.equal(odd.kind, 'REGISTERED');
  assert.equal(odd.plan, 'FREE');
  assert.equal(odd.guest_expires_at, null);
});

test('a different Firebase uid is a different user (accounts are never merged)', () => {
  const { store } = setup();
  const a = store.upsertSession({ uid: 'u1', provider: 'anonymous' });
  const b = store.upsertSession({ uid: 'u2', provider: 'anonymous' });
  assert.notEqual(a.id, b.id);
});

test('findByUid returns null for unknown users and the row for known ones', () => {
  const { store } = setup();
  assert.equal(store.findByUid('nobody'), null);
  const u = store.upsertSession({ uid: 'u1', provider: 'anonymous' });
  assert.equal(store.findByUid('u1').id, u.id);
});

test('publicShape exposes what the app needs and never the Firebase uid', () => {
  const { clock, store } = setup();
  const row = store.upsertSession({ uid: 'secret-firebase-uid', provider: 'anonymous' });
  const shape = store.publicShape(row);
  assert.deepEqual(Object.keys(shape).sort(),
    ['createdAt', 'guestExpiresAt', 'guestStartedAt', 'kind', 'plan', 'serverTime', 'userId']);
  assert.equal(shape.userId, row.id);
  assert.equal(shape.serverTime, clock.t);
  assert.ok(!JSON.stringify(shape).includes('secret-firebase-uid'));
});

test('many concurrent first calls still produce exactly one user', () => {
  const { db, store } = setup();
  for (let i = 0; i < 20; i++) store.upsertSession({ uid: 'same', provider: 'anonymous' });
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM users').get().n, 1);
});

test('deleting a user removes only that user, and deleting again is harmless', () => {
  const { store } = setup();
  store.upsertSession({ uid: 'u1', provider: 'anonymous' });
  store.upsertSession({ uid: 'u2', provider: 'google.com' });

  assert.equal(store.deleteByUid('u1'), true);
  assert.equal(store.findByUid('u1'), null);
  assert.ok(store.findByUid('u2'), 'the other user is untouched');
  assert.equal(store.deleteByUid('u1'), false);        // again: nothing to delete, no error
  assert.equal(store.deleteByUid('never-existed'), false);
  assert.ok(store.findByUid('u2'));
});

test('a user deleted and signing in again is a brand-new user with a fresh guest period', () => {
  const { clock, store } = setup();
  const first = store.upsertSession({ uid: 'u1', provider: 'anonymous' });
  clock.t += 20 * DAY;
  store.deleteByUid('u1');
  const again = store.upsertSession({ uid: 'u1', provider: 'anonymous' });
  assert.notEqual(again.id, first.id);
  assert.equal(again.guest_started_at, clock.t);
});

test('an email/password account is a guest until its email is verified, then a registered Free user (same user)', () => {
  const { store } = setup();
  const guest = store.upsertSession({ uid: 'e1', provider: 'anonymous' });
  // linking an email keeps the uid; while unverified the token counts as anonymous, so nothing changes
  const stillGuest = store.upsertSession({ uid: 'e1', provider: 'anonymous' });
  assert.equal(stillGuest.kind, 'GUEST');
  assert.equal(stillGuest.guest_expires_at, guest.guest_expires_at);
  // after verification the token says "password"
  const registered = store.upsertSession({ uid: 'e1', provider: 'password' });
  assert.equal(registered.id, guest.id);
  assert.equal(registered.kind, 'REGISTERED');
  assert.equal(registered.plan, 'FREE');
  assert.equal(registered.guest_expires_at, null);
});

test('a brand-new verified email account is registered at once, and an unverified one starts as a guest', () => {
  const { store } = setup();
  assert.equal(store.upsertSession({ uid: 'v1', provider: 'password' }).kind, 'REGISTERED');
  assert.equal(store.upsertSession({ uid: 'u1', provider: 'anonymous' }).kind, 'GUEST');
});
