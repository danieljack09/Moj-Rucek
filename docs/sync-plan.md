# Sync between phone and server (roadmap phase 4)

Status: **plan for approval, nothing built yet.** Written 2026-11-25.

## 1. What this has to achieve

1. **A new phone gets your data back.** Sign in with the same account and your pantry, your own recipes, plans, shopping list and favourites are there.
2. **Two phones on one account** can use the same data (one after the other, not at the same moment).
3. **Safe:** syncing never silently throws away someone's changes. Nothing is deleted without a safety copy. Failures are quiet and never block the app.
4. **Works for everyone the same way:** Guest, Free and Paid (the rules table says sync is "Yes" for Guest and Free). Nothing about sync depends on the plan.

## 2. The approach: one snapshot per account, not row-by-row merging

Two ways to sync:
- **Row-by-row merge** (every item has its own change time, deletions are remembered, two phones' edits are merged). Most powerful, but it changes every screen's data code, and mistakes silently lose or duplicate data. This is a large, risky piece of work.
- **One snapshot per account** (the phone uploads its whole data set as one file; another phone downloads it). Much simpler, and it **reuses the backup code that is already built and tested** (it exports and imports in one all-or-nothing step, remaps ids, validates the file). Its limit: if two phones changed data separately, the app cannot merge them; it asks which one to keep.

**Decision: snapshot sync now.** For a lunch planner used by one household it covers the real needs (new phone, switching phones, a second phone). Row-by-row merging can come later on top of the same server table if it is ever needed.

## 3. How it behaves

The server keeps **one snapshot per account** plus a **revision number** (1, 2, 3, ...). The phone remembers, per account, the revision it last synced and a fingerprint (hash) of its data at that moment. Comparing fingerprints tells the phone reliably whether its data changed since the last sync, without touching every data function.

| Situation | What happens |
|---|---|
| Nothing on the server yet, phone has data | Upload (revision 1). |
| Phone unchanged, server unchanged | Nothing. |
| Phone changed, server unchanged | Upload (revision + 1). |
| Phone unchanged, server has a newer revision | Download and replace the phone's data (after a safety copy). |
| Phone changed AND server has a newer revision | **Conflict:** a dialog asks "Keep this phone's data" or "Use the cloud copy". A safety copy of the phone's data is always made first. |
| New phone, empty, server has data | Download automatically. |
| New phone with data of its own, server has data | Conflict dialog (same as above). |

The upload only succeeds if the phone's last-known revision is still the server's current one (the server checks this), so two phones can never overwrite each other by accident.

**What is in the snapshot:** pantry, the user's own recipes with their ingredients, favourites, weekly plans with planned meals, the shopping list. **What is not:** reminder and servings settings (they belong to the phone), the server recipes and catalog (the same for everyone), unlocked recipe details (they come from the server's list rules), and the account itself.

**When it runs** (always quiet, never blocks): at app start, when the app goes to the background after changes, every 12 hours, and with a manual **"Синхронизирај сега"** button. If the phone is offline a single background retry waits for internet (the same pattern as the session retry).

**Settings screen:** a small "Синхронизација" card: last sync time, status, the button. Hidden when signed out.

## 4. Server design

- New table `user_snapshots` (account id, revision, updated time, size, the snapshot text). The server treats the snapshot as opaque text: it is never executed and never parsed beyond checking it is JSON with a known format number.
- `GET /api/sync` returns only the revision, time and size (nothing, revision 0, if none). `GET /api/sync/data` returns the snapshot. `PUT /api/sync` uploads `{baseRevision, data}`; a wrong base revision gets `409` with the current revision.
- Size limit 2 MB per snapshot (a normal account is a few kilobytes), the existing sign-in and rate limits apply, and each account can only ever reach its own row.
- **Deleting an account also deletes its snapshot** (the same hook as the list records).
- Everything is covered by tests: first upload, conflict, size limit, wrong user, delete, repeat.

## 5. Phone design

- Pure decision logic (the table above) with its own tests, a sync coordinator with a fake server, export and import **without settings** for sync (a new option on the existing backup code; the normal backup is unchanged), a safety copy before any replacement, per-account sync memory, triggers, one background retry job, and the Settings card with the conflict dialog.
- Account switch (guest into an existing account) already clears the guest's data after a verified copy; the existing account's snapshot then downloads automatically.
- Deleting the account also clears its sync memory.

## 6. Privacy consequence (to be done together with the feature)

Today the privacy policy and the Play Data safety answers say the pantry, recipes, plans and shopping list "stay only on your phone". **That stops being true.** With sync they are stored on our server. The policy text, the retention and deletion wording and the Data safety form must be updated at the same time (the new data counts as "user-generated content / app activity", collected, not shared, encrypted in transit). Snapshots are not encrypted by the app on the server's disk; anyone with access to the server (you, as the owner) could technically read them. Say if you want client-side encryption later; it is not part of this step.

## 7. Order of work

1. **S1 server:** table, endpoints, delete hook, tests, deploy.
2. **S2 phone logic:** decision rules, coordinator, export/import without settings, safety copy, sync memory; JVM and phone tests.
3. **S3 triggers and screens:** start/background/12 h/manual triggers, retry job, Settings card, conflict dialog; tests.
4. **S4 real test and paperwork:** reinstall the app on your phone and sign in (data must come back), a conflict test, then update the privacy page and the Data safety draft.
Each step: tests, build, phone check, your OK before commit.

## 8. Decisions I made for you (change any)
- Snapshot sync instead of row-by-row merging.
- A conflict is decided by you in a dialog (never automatically), and a safety copy is always made first.
- Reminder settings are not synced.
- 2 MB limit per account.
