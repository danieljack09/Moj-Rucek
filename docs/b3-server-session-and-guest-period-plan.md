# B3: telling the server who is signed in, and the guest's 14 days

Status: **plan for approval, nothing built yet.** Written 2026-11-23. Roadmap phases 3 (backend authentication, app side) and the guest part of 7.

## 1. What this has to achieve

1. After someone signs in, the app tells the server once ("this is me"), so the server creates the user and **starts the guest's 14 days** at that first successful call.
2. The phone remembers what the server answered, so it can show "Guest, 11 days left" and remind the user **3 days and 1 day before the period ends**, even when offline.
3. After the period has ended, a **non-blocking** dialog (at most once a day) invites the guest to link a Google account. **Nothing is restricted yet**: the real Free/Guest limits come later and are enforced on the server.
4. Nothing here may ever block the app, show an error to the user, or crash. If the server cannot be reached, the app carries on and tries again later.

This is also the **first time a real Firebase sign-in token reaches the server**, so it doubles as the real-world test of the server's token checks (B1), which so far were only tested with test tokens and Google's public keys.

## 2. What the server already provides (nothing to change on the server)

`POST /api/session` with `Authorization: Bearer <Firebase ID token>` creates the user on the first call and returns:
`{ userId, kind (GUEST|REGISTERED), plan (GUEST|FREE), createdAt, guestStartedAt, guestExpiresAt, serverTime }`.
Linking Google to a guest keeps the same user, turns it into REGISTERED/FREE and removes the expiry. `GET /api/me` returns the same for an existing user. Errors: 401 (bad/missing token), 429 (rate limit), 503 (not configured / Google keys unreachable). **No server change and no server deploy is needed for B3.**

## 3. Decisions (and why)

**D1. When the app calls the server.**
- Shortly after an account becomes the current one, when online (right after sign-in, and at app start for an already signed-in account, at most once every 12 hours).
- Immediately when the account type changes (a guest links Google), so the expiry disappears at once.
- If the phone is offline or the call fails: one background job (WorkManager, "needs internet", unique, backs off) tries again later. Never more than one at a time.

**D2. What is sent.** Only the Firebase ID token in the request header. No recipes, ingredients, lists or plans leave the phone. (App Check tokens are a separate, later item.)

**D3. What the phone remembers.** The last answer per account (keyed by the Firebase id): user id, type, plan, guest start/end, how far the phone clock was from the server clock, and when it last refreshed. Stored in the app's private preferences, **not in the database**, so the version-4 database stays untouched. Two accounts on one phone never share it.

**D4. The guest clock.**
- It starts on the server at the first successful call. An offline first launch has **no countdown** until the first success.
- "Days left" uses the server's time (corrected by the clock difference measured at the last sync), so a wrongly set phone date at that moment does not skew it. Changing the date later can only affect reminders and the dialog, never access, because nothing is restricted in this phase.

**D5. Reminders (3 days and 1 day before the end).** Scheduled with WorkManager from the stored end time, at **10:00 local time** (the same default hour as the other reminders). Re-planned after every refresh, cancelled when the account becomes registered, skipped if their time has already passed. A reminder only fires if the current account is still that guest. They use their own notification channel ("Гостински период"), so the user can mute them.

**D6. After the period ends.** A dialog "Гостинскиот период истече" with **Поврзи со Google** and **Подоцна**, shown when the app opens, at most **once per day**. It never blocks anything.

**D7. Settings.** The account card shows "Гостин · уште N дена", "Гостин · периодот истече", or "Google · Free" once the answer is known, and nothing extra while it is not.

**D8. Switching to an existing account** (the guest conflict flow) loads that account's own session; the guest's stored answer is kept but unused, and the guest's reminders stop (they check who is signed in when they fire).

**D9. Failures are quiet.**

| What happens | What the app does |
|---|---|
| no internet | try again later (background job) |
| 401 (token rejected) | ask Firebase for a fresh token once and retry once; then stay quiet until the next trigger |
| 429, 503, other server errors | back off and retry later |
| unreadable answer | ignore it, keep the last good one |
| anything unexpected | never crash, never show an error |

**D10. Not part of B3.** Any restriction of features, App Check from the app, the Free recipe limits and the split of the public recipe list (all later phases), and any server change.

## 4. The pieces

- `ServerConfig`: the server address in one place (shared with the catalog sync).
- `GuestPeriod`: plain date logic with no Android in it: days left, state (running / ending soon / expired / registered), reminder times, "should the dialog show today".
- `SessionApi`: the one HTTP call and the reading of the answer.
- `SessionStore`: the per-account memory (preferences).
- `SessionRepository`: ties them together: refresh, store, schedule.
- Workers: a retry job for the refresh, and the two reminder jobs.
- UI: the extra line on the account card and the expired-guest dialog.
- Hook-ups: the place where the app already reacts to the current account changing.

## 5. Build order: small steps, each tested before the next

| Step | What | Tested how |
|---|---|---|
| **B3-1** | `GuestPeriod` (days left, states, reminder times, dialog rule) | many unit tests on the computer: boundaries (exactly 3 days, 1 day, 0), wrong clocks, time zones, daylight-saving days |
| **B3-2** | `SessionApi`, `SessionStore`, `SessionRepository` | unit tests against a small fake server on the computer: success, 401 then retry, 429, 503, garbage answer, offline, two accounts kept apart |
| **B3-3** | the triggers and the retry job | tests on the phone (WorkManager test tools): offline then online, no duplicate jobs, account switch |
| **B3-4** | reminders, the expired dialog, the account card text | tests on the phone, plus a screen test for the dialog |
| **B3-5** | real run on your phone | you are signed in with Google, so the server should answer "REGISTERED / FREE"; the card shows it. Then, if you want, a guest test: sign out, continue as guest, see "уште 14 дена" |

## 6. Risks

| Risk | Mitigation |
|---|---|
| a server problem makes the app misbehave | every failure is quiet and non-blocking; the app works fully without any answer |
| a reminder fires for the wrong person | it checks who is signed in at the moment it fires |
| duplicate reminders or retry jobs | unique job names; replaced, not added |
| the phone clock is wrong | only affects reminder timing and the dialog; nothing is restricted |
| the first real tokens expose a mistake in the server's token check | this is the real test of B1; the server fails closed (401), so the result is "no session", never wrong access |

## 7. Decided on your behalf (change later if you disagree)

- Reminders at 10:00 local time, 3 days and 1 day before the end.
- The expired dialog once per day, never blocking.
- Session answer stored in private preferences, not in the database.
- A refresh at most every 12 hours at app start.
- Settings shows the days left for guests.
- Texts in Macedonian, as everywhere else.
