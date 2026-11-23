# Room v4: account ownership and sync groundwork

Status: **design for approval, nothing built yet.** Written 2026-11-23. Roadmap phases 2 (account-aware local database) and the groundwork for 4 (sync) and 7 (Guest/Free limits).

## 1. What this has to achieve

1. **Switching accounts on a phone must never show another person's data.** Today all data on the phone belongs to "whoever is signed in" (a known Stage A limitation).
2. **No data is lost** when existing phones upgrade. The existing data has to survive and end up in the right account.
3. **Be ready for sync and for the Free/Guest rules** without needing a second risky change to tables that already hold user data.

The Free/Guest rules this must support (from the roadmap and the owner, clarified 2026-11-23): a Free user can see **all** recipes, but only their **name, cooking time and ingredients**. The user picks **3 recipes** and generates a shopping list for them; after that there is a **72-hour lockdown** before the next list. The next list may use the same recipes again (confirmed by the owner); the 3 recipes of one list are 3 distinct recipes. The 3 recipes of a generated list unlock fully (cooking instructions and the rest). How long they stay unlocked, and exactly which fields count as "the rest", is **deliberately left open**. Guests have 14 days. These rules are enforced on the server (Phase 7); this document only makes sure the phone's database does not get in the way. Nothing in the version-4 database depends on the open points: unlocks will be a new table later (D6), with an optional end time so either answer fits.

## 2. What belongs to whom

| Data | Belongs to | Notes |
|---|---|---|
| Pantry ingredients (`ingredients`) | the signed-in user | |
| Shopping list (`shopping_list_items`) | the signed-in user | |
| Weekly plans and planned meals (`weekly_plans`, `planned_meals`) | the signed-in user | meals follow their plan |
| Recipes the user created (`recipes` where `isUserCreated`) and their lines | the signed-in user | |
| Favourites | the signed-in user | today a flag on the recipe row, so shared by everyone on the phone |
| Server recipes and the ingredient catalog (`catalog_ingredients`, recipes with a `serverId`) | **nobody** (shared catalog) | the same for every account |
| Reminder and servings settings (`app_settings`) | the phone | stays per device |

## 3. Decisions (and why)

**D1. The owner key is the Firebase user id.** It is known immediately and offline, and it stays the same when a guest links Google (Firebase keeps the id). The server's own `usr_...` id is only known after the first server call, so it cannot be the local key.

**D2. Every user-data row carries an `ownerUid`, and every query filters by it.** This is the direct way to meet "never show another person's data". The code is arranged so a query cannot be written without an owner: the data-access functions take the owner as a required parameter, and one place (`CurrentOwner`, fed by the sign-in state) supplies it. About 33 queries are affected (Ingredient 7, Planner 9, Recipe 13, Shopping 4); the settings and catalog queries are not.

**D3. Existing data starts unowned.** The migration sets `ownerUid = ''` on every existing row.
- If someone is signed in when the app first opens after the upgrade (the normal case), **they claim all unowned data automatically**. Until now the phone has been single-user, so the person using it is the owner.
- If nobody is signed in then, the data stays unowned. After the next sign-in the app shows a one-time question: **"There is data from before accounts on this phone. Keep it in this account?"** with **Keep** and **Save a copy first**. Nothing is deleted or uploaded automatically.

**D4. Groundwork for sync is added now, switched on later.** To every user-data table the migration adds: `uuid` (a stable identity across phones, filled in for existing rows), `deletedAt` (empty for now), and `dirty` (a "changed since last sync" flag). `updatedAt` is added where missing (`ingredients` already has it).
Why now: adding columns to tables that already hold user data is the risky kind of change, and doing it once is better than twice. Why only the columns: the *behaviour* (soft deletes, filtering deleted rows, marking dirty) changes many more places, and belongs to the sync phase where it can be tested properly. Until then the columns are present but unused.

**D5. Favourites get their own table** (`recipe_favorites`: owner, server recipe id or recipe uuid, updatedAt). Existing favourite flags are copied into it for the owner, so favourites stop being shared between accounts.

**D6. Later phases add NEW tables only.** The Free/Guest unlock data (steps of unlocked recipes with an end time) will be a new table in a later version. Adding an empty table is the safest kind of migration, so it does not need to be squeezed into v4.

**D7. Account switching keeps its safety flow, scoped by owner.** The guest-to-existing-Google flow (snapshot, explicit confirmation, sign in, then clear) stays exactly as agreed, with one change: the "clear local data" step may only delete rows owned by the guest, never anyone else's. This is a required fix, because without it the clear step could delete another owner's data.

**D8. Backup and restore are scoped to the current owner.** Export writes the current owner's data (the file format stays version 1, so old backups still restore). Restore stamps the current owner on what it imports. A backup never contains another account's data.

**D9. Safety net for the upgrade itself.**
- Before the first v4 open, the app **copies the whole database file aside** (`mojrucek-v3-before-upgrade.db`), so a failed or surprising migration can always be undone.
- Room's schema files (`app/schemas/`) are currently git-ignored. They are needed for migration tests, so they should be **committed** from now on.
- A migration test upgrades a v3 database filled with realistic data and checks every row, uuid, owner and favourite.

## 4. Build plan: small steps, each tested before the next

| Step | What | Visible change | How it is tested |
|---|---|---|---|
| **v4-1** | Schema v4 + migration + the database copy-aside + commit `app/schemas/` | none | migration test on a filled v3 database (computer + phone); upgrade of the phone's real data (after copying its database to the computer first) |
| **v4-2** | `CurrentOwner`, owner-scoped data access, stamping owner on new rows, automatic claim of existing data | none for one user; accounts no longer see each other's data | two-account isolation tests; switch accounts on the phone |
| **v4-3** | Favourites table and the move of existing favourites | favourites become per account | unit + phone test |
| **v4-4** | Account switch/clear and backup/restore scoped by owner | none | existing switch and restore tests extended; phone test |
| **v4-5** | The one-time "data from before accounts" question (signed-out case) | one new dialog | UI test on the phone |

v4-2 is the largest step (about 33 queries plus the repositories). It is split from v4-1 on purpose: v4-1 changes only the database shape and cannot change behaviour, so any problem is easy to find.

## 5. Risks

| Risk | Mitigation |
|---|---|
| A migration bug damages existing data | database copy-aside before upgrade; migration test on realistic data; the real phone upgraded last, after copying its database |
| A forgotten query still shows everyone's data | owner is a required parameter, so a missing one does not compile; isolation tests with two accounts |
| The clear step deletes another account's data | D7: clearing is limited to the guest's owner id; tested |
| Users with the old app version | no server change is part of v4; the server API is not touched here |

## 6. What I decided on your behalf (change later if you disagree)

- Owner key = Firebase uid; per-row ownership (not "one owner per phone").
- Existing data is claimed automatically by whoever is signed in at upgrade time; if nobody is, they are asked once.
- Sync columns are added now, behaviour later; unlock tables come later as new tables.
- "3 days" = 3 planned days, one recipe each; the same recipe may be used on more than one day.
- Free users see only name, cooking time and ingredients; everything else (steps, description, notes, tags) is locked until a list is generated (the owner's own words; replaces my earlier "description visible" guess).
- Recipes may be reused: after the 72 hours the next list can use the same recipes again (confirmed by the owner).
- Creating a shopping list needs internet for Free users; everything else works offline.
- Schema files are committed to git.

## 7. Not part of v4

- Soft-delete behaviour, marking rows dirty, and the sync itself (Phase 4).
- Server tables for generations/unlocks, the "generate list" endpoint, hiding steps from the public catalog (Phases 5 to 7).
- Changing the agreed guest-to-Google flow beyond the scoping fix in D7.
