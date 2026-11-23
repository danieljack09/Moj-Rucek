# Phase 8: deleting an account, and email/password sign-in

Status: **plan for approval, nothing built yet.** Written 2026-11-23.

## 1. Why now
Google Play will not accept an app that lets people create an account but not delete it (in the app AND via a web page). Email/password sign-in is the second way into the app besides Google. Both build on the account work already done (B1-B3).

## 2. Deleting an account (the must-have)

**What the user sees:** Settings > account card > "Избриши сметка". A clear dialog says what is deleted (the account, and the data on this phone that belongs to it; the server record), that it cannot be undone, and offers "Зачувај копија прво" (the existing backup). The user must tick "Разбирам" before the red button works.

**Order of steps (so a failure never leaves a half-deleted mess):**
1. Ask Firebase to confirm it is really the owner (Google accounts: sign in with Google again; email accounts: password). Firebase demands this for deletions anyway.
2. Tell our server: `DELETE /api/me` (Firebase token in the header). The server removes the user row (later also anything else it stores for that user). Answer 204. Deleting twice is fine (still 204).
3. Delete the Firebase account (`user.delete()`).
4. Delete the phone data that belongs to that account (rows whose ownerUid is that account, favourites, remembered server answer, reminders). Other accounts' data and data from before accounts are never touched.
5. The app returns to the welcome screen.
If step 2 or 3 fails (no internet, etc.) nothing local is deleted, the user sees "Не успеа, обиди се повторно" and the account still works. If step 3 fails AFTER step 2, the retry simply repeats steps 2-3 (both are safe to repeat).

**Guests** can delete too (same flow, no re-sign-in needed for an anonymous account).

**Web page for Google Play:** a plain page at `rucek.display.mk/delete-account` explaining how to delete in the app and how to ask for deletion by email if the app is gone. (Needs the user's contact email: decision below.)

## 3. Email/password (second)
- Sign-in / create-account / "forgot password" screens (Firebase sends the reset email, no server work).
- New accounts must **verify their email** (Firebase sends the link). Until verified, the app works as a guest-like account and the server does not upgrade it to REGISTERED. (Avoids fake-address accounts getting a free plan.)
- Server: allow the `password` provider in `ALLOWED_PROVIDERS` and require `email_verified` for it. Google accounts are unchanged.
- A guest can link an email/password the same way as Google (same data kept).

## 4. Tests
Server: delete is idempotent, only deletes the caller's row, 401 without a token. App: pure logic for the step order and failure handling (each step failing at each point), a real-database test that only the deleted account's rows go, and a phone test with a fake Firebase layer. Real run on the phone with a throwaway guest account.

## 5. Order of work
D1 server `DELETE /api/me` + tests + deploy -> D2 app deletion flow + tests -> D3 web page -> E1 email/password screens + server provider rule. Each step: tests, build, phone test, then your OK before commit.

## 6. Decisions for the user
1. Contact email to show on the deletion page (can be changed later).
2. Email accounts: require verifying the email (recommended) or not.
