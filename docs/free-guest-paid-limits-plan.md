# Guest / Free / Paid: what each user can do, enforced on the server

Status: **plan for approval, nothing built yet.** Written 2026-11-24, from the rules the user wrote on 2026-11-24 (they replace every earlier guess).

## 1. The rules (as the user defined them)

| | Guest | Free | Paid |
|---|---|---|---|
| Browse all recipes | yes | yes | yes |
| Recipes chosen per list | up to 3 | up to 3 | unlimited |
| Make a shopping list | 1 per 72 h | 1 per 72 h | unlimited |
| Full recipe details | after making a list | after making a list | always |
| Edit list, mark bought, own recipes | yes | yes | yes |
| Data kept | until linking | long term | long term |
| Time limit | 14 days | none | none |

- 72 hours count from the moment a list is **successfully** made (server clock), not from midnight.
- Guest after 14 days: cannot make a **new** list until linking Google. Everything else keeps working, nothing is deleted.
- Linking Google changes Guest to Free: same person, same 72-hour clock keeps running.
- Paid ends: back to Free, nothing deleted; Free limits apply to new lists.
- Answers given on 2026-11-24: details **lock again** when the 72 hours end; making a list **needs internet**; only the Free/Guest limits plus a "Paid lifts them" type are built now (the other Premium benefits come later).

## 2. What "locked" means for a recipe

Everyone sees (public, offline): **name, category, servings, prep and cooking time, ingredients with quantities.** That is what is needed to choose recipes and to build the list.
Locked until a list is made: **description, cooking steps, notes, tags.** (Default: description is locked too. Easy to change: one line on the server.)
Own recipes (the user created them) are never locked.

Consequence: the public catalog must stop sending the locked fields, otherwise the limit means nothing. The unlocked fields come only from a signed-in request that the server has approved.

## 3. Server design (the real enforcement)

**New table `generations`:** id, user id, request id, created at (server time), and `generation_recipes` (which recipes were chosen). Nothing else about the list leaves the phone: the server never sees the list items.

**`POST /api/generations`** (signed in) with the chosen recipe ids and a request id made by the app:
1. Free/Guest: 1 to 3 different, existing, not-deleted recipes. Paid: any number (sane upper limit, e.g. 50).
2. Guest whose 14 days are over: refused with a clear code `GUEST_EXPIRED`.
3. Free/Guest with an earlier list less than 72 h ago: refused with `TOO_SOON` and the exact time the next list is allowed.
4. Otherwise: the list is recorded and the answer contains `createdAt`, `unlockedUntil` (= created + 72 h; none for Paid) and the **full details of the chosen recipes**.
5. **Safe retry:** the same request id from the same user returns the same answer and does not use up another 72 hours. So a lost connection never costs the user their list.
6. All of it inside one database transaction, so two phones of one account cannot both succeed at the same moment.

**`GET /api/unlocks`** (signed in): the current state: when the next list is allowed, until when the details are unlocked, and the full details of the currently unlocked recipes (the last list's recipes while its 72 h run; all recipes for Paid). Used at app start and after sign-in to restore the unlocked state, for example on a new phone.

**`/api/me` and `/api/session`** also return the plan (`GUEST`, `FREE`, `PAID`). Paid is set later by billing; until then it is changed by hand in the admin panel for testing.

**Other server changes:** deleting an account also deletes its generations; sign-in, rate limits and error messages follow the existing style (vague for attackers, clear codes for the app). Everything uses server time.

**Catalog switch:** the public `/api/catalog` keeps sending everything until the new app is ready; then one setting (`CATALOG_LOCKED=1`) makes it send only the public fields. No flag day, no broken phone in between.

## 4. App design

- **Room v5 (new table only, no change to old tables):** `unlocked_recipe_details` (owner, recipe server id, description, steps, notes, tags, unlocked until or none for Paid). The details screen shows the details only while a row exists and has not run out (using the server-corrected clock); otherwise it shows a lock with "Направи листа за да ги отклучиш".
- **Details already on the phone:** earlier syncs saved steps and descriptions of server recipes in the recipes table. The v5 upgrade removes them there (own recipes untouched), after a safety copy of the database like v4 did. Backups of own data are unchanged.
- **Making a list:** the app first asks the server (`POST /api/generations`). On success it builds the list locally as today and stores the unlocked details. On `TOO_SOON` it says when the next list is possible; on `GUEST_EXPIRED` it invites to link Google (non-blocking, as in B3); offline it says internet is needed. Editing, ticking items and own recipes never need internet.
- **Settings/Recipes show:** the countdown to the next list and the plan name.
- The weekly planner stays as it is: the 3-recipe limit applies where the list is made. Separate "whole week is Paid" limits in the planner screen are a later nudge, not a security rule.

## 5. Order of work

1. **F1 server:** tables, endpoints, plan type, catalog switch (off), delete-account cleanup, tests (limits, 72 h edges, retry safety, concurrency, expired guest, Paid). Deploy; nothing changes for the current app.
2. **F2 app data:** Room v5 table + migration test + cleanup of old details, with the real phone's database copied to the PC first.
3. **F3 app flow:** the generation request, locked/unlocked recipe screens, messages, countdown; tests.
4. **F4 switch on:** `CATALOG_LOCKED=1`, real run on the phone (guest and Google), then your approval to commit.
Each step: tests, build, phone check, your OK before commit.

## 6. Decisions I made (change any of them)
- Description is locked (not public).
- While the 72 hours run, tapping another recipe for a new list is not possible anyway, because a new list is not allowed yet.
- Paid maximum recipes per list: 50.
- If a Paid subscription ends, the last generation time still counts for the 72-hour rule.
