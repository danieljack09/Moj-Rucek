# Redesign verification (visual-only check)

Date: 2026-11-26. Emulator: Medium_Phone_API_37.0, light mode, signed in as a guest.
Baseline: there is no `redesign-start` tag. The redesign is uncommitted work on top of `HEAD` = `0a753c3`, so `HEAD` is the "before".

Legend: **PASS** = checked on the emulator in this run (or earlier, where it says so) · **FAIL** = does not work · **PRE-EXISTING** = fails, but the code is identical to `HEAD` · **NOT RUN** = state not reachable, or the action is irreversible / external.

## 1. Changed files and paths
- 29 tracked files modified, all under `app/src/main/java/com/mojrucek/app/ui/` or `app/src/main/res/`.
- New files: `ui/components/` (24), `ui/recipes/RecipeCover.kt`, `ui/theme/` (2), `res/drawable/` (2), `res/font/` (3), `res/values*/colors.xml`, `licenses/`, `docs/redesign/`.
- No `*ViewModel.kt`, `data/`, `domain/`, `sync/` logic, worker or Gradle file was changed by the redesign. `ui/sync/SyncUi.kt` is UI: same service calls and texts as `HEAD`.
- Exception 1: `app/schemas/com.mojrucek.app.data.AppDatabase/1.json` is untracked, dated 2026-11-06 (before the redesign). It is Room's generated schema export (`room.schemaLocation` in `app/build.gradle.kts`), not a redesign change.
- Exception 2: **another session edited this working copy during verification** (21:11–21:12): a new `domain/text/MkPlural.kt` and `MkPluralTest.kt`, and edits to `domain/limits/LimitTexts.kt`, `domain/session/GuestTexts.kt`, `test/.../GuestTextsTest.kt`, `ui/settings/SettingsViewModel.kt`, and plural strings in eight `ui/` screens. That is not redesign work, and it was left untouched.

## 2. Public signatures
- All public screen composables are identical to `HEAD`.
- Shared components:
  - the old `EmptyState(message, …)` and `CategoryEmojiBadge` were deleted (unreferenced in main/test/androidTest);
  - `CategoryChipsRow` gained one optional trailing parameter (`contentPadding`), so existing calls still compile.

## 3. User-facing strings
- After the fix below, every Macedonian literal in `ui/` at `HEAD` still exists.
- **Fixed in this run:** the add buttons had become "Додади намирница" / "Додади чекор" (the "+" was moved into an icon). They are restored to "+ Додади намирница" / "+ Додади чекор".
- New user-facing text, each asked for in a redesign prompt:
  - "Опис" (description heading)
  - "или" (Welcome divider)
  - "Прикажи лозинка" / "Сокриј лозинка" (eye toggle)
  - the picker meta line (category · servings · time, built from existing data)
- All other new literals are `@Preview` sample data.
- Two literals that now differ ("Додадени се … ставки.", "Истекува за $days дена") were changed by the concurrent plurals edit, not the redesign.

## 4. Inventory walkthrough

### App shell
| Item | Result | Note |
|---|---|---|
| Five-tab floating nav, same routes and back stack | PASS | every tab opened in this run |
| AuthGate → APP | PASS | |
| AuthGate → WELCOME / CONFIG_ERROR | NOT RUN | needs signed-out or unconfigured build |

### 1. Welcome
| Item | Result | Note |
|---|---|---|
| Google / email / guest buttons, busy state | NOT RUN | screen only shows when signed out; a guest cannot sign out |

### 2. Email form (opened from Settings)
| Item | Result | Note |
|---|---|---|
| Opens in create mode, guest line shown | PASS | |
| Primary disabled while invalid | PASS | |
| Field errors with helper text | PASS | "Е-поштата не изгледа правилно." / "Лозинката мора да има најмалку 8 знаци." |
| Eye toggle show/hide | PASS | added on request |
| Mode switch Веќе имам сметка / Нова сметка | PASS | title, label, button and link change |
| Заборавена лозинка? (sign-in only) | PASS | invalid email → validation message; no request sent |
| Back circle (Откажи) closes | PASS | |
| Submit Креирај / Најави се | NOT RUN | would create or sign in to a real account |

### 3. Guest period · 4. Legacy data
| Item | Result | Note |
|---|---|---|
| Guest line in AccountCard | PASS | "Гостин · уште 14 дена" |
| GuestExpiredDialog | NOT RUN | guest period not expired |
| LegacyDataPrompt | NOT RUN | no legacy data; its instrumented test fails on the PRE-EXISTING Espresso/API 37 issue (step 5) |

### 5. Home
| Item | Result | Note |
|---|---|---|
| Settings circle | PASS | |
| Избери јадење → picker | PASS | |
| Picker search (Latin → Cyrillic) | PASS | "spag" → Спагети only |
| Picker row sets today | PASS | |
| Picker Испразни ден | PASS | |
| Picker Затвори | PASS | |
| Picker back | PASS | |
| Промени / Отвори рецепт | PASS | |
| Week pill, planned → recipe | PASS | |
| Week pill, empty → Planner | PASS | |
| Намирници при крај → Ingredients with low-stock filter | PASS | no-match state confirms the filter |
| За купување → Shopping | PASS | |
| Weekly reminder → next week in Planner | PASS | |
| Loading state | NOT RUN | too brief to catch |

### 6. Ingredients
| Item | Result | Note |
|---|---|---|
| Sort menu (4 options) and sorting | PASS | |
| Category chips | PASS | |
| Search | PASS | |
| При крај / Истекува наскоро chips | PASS | |
| −/+ stepper | PASS | 2 → 3 → 2 |
| Row tap → edit | PASS | |
| ⋮ Уреди → edit | PASS | |
| ⋮ Избриши | PASS | test rows deleted |
| FAB → add sheet | PASS | |
| Save disabled when empty / quantity −1 | PASS | |
| Suggestions; pick fills name and category | PASS | |
| Unit dropdown | PASS | |
| Date picker Во ред / Откажи / Отстрани датум | PASS | |
| Close circle cancels | PASS | |
| Edit save (threshold → low-stock dot) | PASS | |
| Duplicate dialog: Ажурирај количина | PASS | 2 → 3 |
| Duplicate dialog: Додади посебно | PASS | new row |
| Duplicate dialog: back dismisses | PASS | no change |

### 7. Recipes
| Item | Result | Note |
|---|---|---|
| Readiness chips (4) | PASS | |
| Омилени | PASS | |
| Time chips (3) | PASS | |
| Search | PASS | |
| Heart toggles favourite | PASS | on → appears under Омилени; off → gone |
| FAB → new recipe | PASS | |
| Card → detail | PASS | |
| Empty / no-match state | PASS | Омилени + Треба да купам → empty |

### 8. Recipe detail
| Item | Result | Note |
|---|---|---|
| Back | PASS | |
| Heart | PASS | |
| Edit | PASS | |
| Copy | PASS | created "test (копија)" |
| Delete dialog Откажи | PASS | |
| Delete dialog Избриши | PASS | on the copy |
| Servings stepper | PASS | scales quantities; minimum 1 |
| Cook dialog Откажи | PASS | |
| Cook dialog Потврди | PASS | inventory deducted, snackbar shown; inventory restored afterwards |
| Додади ги сите… | PASS | list 6 → 12; reverted with partial purchase |
| Оди во листата за пазарење appears and opens Shopping | PASS | |
| Add-missing snackbar + Кон пазарење action | PRE-EXISTING | snackbar never appears: `clearAddedFlag()` runs before `showSnackbar`, which cancels the `LaunchedEffect`. Same code at `HEAD`. |
| Locked state | PASS | checked in the recipe-detail task (catalog recipe) |

### 9. Recipe edit
| Item | Result | Note |
|---|---|---|
| Back circle; Уреди / Нов рецепт titles | PASS | |
| All fields accept input | PASS | name, description, servings, prep, cook, tags, notes |
| Category dropdown | PASS | |
| Ingredient name / quantity / unit dropdown | PASS | |
| Add / remove ingredient line | PASS | |
| Add / remove step | PASS | |
| Servings error border | PASS | |
| Save disabled while invalid | PASS | checked in the recipe-edit task (this run's check hit an off-screen button) |
| Зачувај рецепт saves | PASS | on the copy |
| Back leaves without saving | PASS | |

### 10. Planner
| Item | Result | Note |
|---|---|---|
| Prev / next | PASS | |
| Tap range → this week | PASS | |
| Household servings ± | PASS | 3 → 4 → 3 |
| Planned card → recipe | PASS | |
| Промени → picker | PASS | |
| Empty-day + → picker | PASS | |
| Copy from another day | PASS | |
| Recipe row sets meal | PASS | |
| Испразни ден | PASS | |
| Затвори | PASS | |
| Incomplete-days warning | PASS | |
| Генерирај листа | NOT RUN | writes a list and uses the server quota |
| Limit / generation-result dialogs | NOT RUN | |

### 11. Shopping
| Item | Result | Note |
|---|---|---|
| FAB → add sheet | PASS | |
| Додади disabled when invalid | PASS | |
| Unit dropdown | **FAIL → FIXED** | regression from the consistency pass: a `sed` removed `.weight(1f)` from the quantity and unit fields, pushing the unit dropdown off-screen. Weights restored and verified. |
| Category dropdown | PASS | |
| Close cancels | PASS | |
| Додади adds | PASS | |
| Segment Во инвентар → Врати | PASS | |
| Segment За сега → Врати | PASS | |
| Segment Избриши → Врати | PASS | |
| Long-press Во инвентар → sheet with checkbox on | PASS | |
| Long-press За сега → sheet with checkbox off | PASS | |
| Partial: Половина / Сè / Потврди | PASS | |
| Swipe right → to inventory | PASS | |
| Swipe left → delete | PASS | |
| Half-swipe pulled back does nothing | PASS | |
| Врати after a swipe | PRE-EXISTING | item comes back and is deleted again: the saved swipe state restores "dismissed" and the effect re-fires. `SwipeableShoppingCard` is identical to `HEAD`. |
| Category headers and count line | PASS | |
| Empty state | PASS | checked in the Shopping task |

### 12–15. Settings, account, sync, delete account
| Item | Result | Note |
|---|---|---|
| Back circle | PASS | |
| Default servings ± | PASS | |
| Reminder switch off/on | PASS | restored to on |
| Неделно / Дневно | PASS | |
| Day circles (weekly single, daily multi) | PASS | |
| Hour / minute steppers with wrap | PASS | restored to 09:00 |
| Catalog Синхронизирај → message dialog | PASS | |
| Зачувај на уредот → system save picker | PASS | backed out |
| Сподели копија → chooser | PASS | backed out |
| Вчитај од датотека → file picker | PASS | backed out |
| Import confirm / invalid / save / import message dialogs | NOT RUN | need a file to be picked or written |
| AccountCard guest state | PASS | |
| Поврзи со Google → account picker, busy state | PASS | cancelled |
| Поврзи со е-пошта | PASS | see section 2 |
| Sign-out / unverified email / switch-account dialogs | NOT RUN | guest account |
| Sync card status | PASS | |
| Синхронизирај сега | PASS | |
| Conflict dialog | NOT RUN | no conflict |
| Delete-account row → dialog | PASS | |
| Избриши disabled until ticked | PASS | |
| Checkbox | PASS | |
| Откажи / back keep the account | PASS | |
| Password field (email accounts only) | NOT RUN | guest account |

## 5. Builds and tests
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, 333 unit tests, 0 failures.
- `connectedDebugAndroidTest` (run with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` so the emulator's data was kept): 110 tests, **107 passed**, 3 failed.
  - The failures are all in `LegacyDataPromptDialogTest`: `NoSuchMethodException: InputManager.getInstance` inside Espresso's idle check, before any assertion.
  - The same three fail identically on `HEAD` (checked in a temporary worktree), so this is PRE-EXISTING.
  - Fixing it means upgrading the Espresso/Compose test dependency, which is a Gradle change.

## 6. Screenshots
Light mode: `docs/redesign/after/light/` (65 files). Dark mode: `docs/redesign/after/dark/`.

## Emulator data after the run
Same as before: grav 2, namirnica 2 4, shopping "namirnica 3" 6 (source "од: test"), Monday planned, today empty, settings unchanged, still the same guest. Every test item (Proba*, the recipe copy) was removed.
