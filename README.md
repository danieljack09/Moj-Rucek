# Мој Ручек

A personal, fully offline Android app for planning household lunches: track what's in your kitchen, keep 50–100 recipes, see what you can cook right now, plan Monday–Sunday, and generate a Sunday shopping list for the week ahead.

Kotlin · Jetpack Compose · Material 3 · Room · Navigation Compose · WorkManager · MVVM. Minimum Android 8.0 (API 26), target/compile SDK 34.

## ⚠️ About this build — please read first

This project was written in a cloud sandbox that has **no Android SDK installed and no network access to Google's or Maven Central's repositories** (the proxy only allows npm/pip/crates/Go/GitHub). That means:

- I could not run `./gradlew assembleDebug`, `./gradlew test`, or `./gradlew connectedDebugAndroidTest` here. **No build or test run has actually happened yet — I am not claiming otherwise.**
- Every source file below was written by hand to compile and behave correctly against documented, stable APIs (AGP 8.5.2, Kotlin 2.0.21, Compose BOM 2024.09.02, Room 2.6.1 — versions pinned in `gradle/libs.versions.toml`), and I did what static checking I could (brace/paren balance, package/directory consistency across all 75 files, re-reading every file for Kotlin syntax). But a first real build on your machine may still surface something — a typo, a version that needs bumping, an API that shifted slightly — that only a real compiler catches.
- The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) *is* real and working — I generated it with a local Gradle 8.14.3 install, so you don't need Gradle installed yourself, just a JDK (Android Studio bundles one).

**What to do:** open the project in Android Studio (Koala/2024.1 or newer) and let it sync. If anything fails to resolve or compile, paste me the error and I'll fix it immediately — that will go much faster than me guessing blind.

## Opening the project

1. Install [Android Studio](https://developer.android.com/studio) if you don't have it (it bundles a JDK and lets you install Android SDK platform 34 + build-tools on first run).
2. `File → Open`, pick the `MojRucek` folder (the one with `settings.gradle.kts`).
3. Android Studio will prompt to install any missing SDK components — accept, then let Gradle sync.
4. Run on your Xiaomi 15T Pro (USB debugging on) or any API 26+ device/emulator via the ▶ Run button.

## Building the APK from a terminal

```bash
cd MojRucek
./gradlew assembleDebug
```

The debug APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Transfer it to your phone (USB, `adb install app/build/outputs/apk/debug/app-debug.apk`, or email/cloud-drive it to yourself) and install it — you'll need to allow "install unknown apps" for whichever app you use to open it. The debug build's application id is `com.mojrucek.app.debug`, so it installs side-by-side with any future release build without conflicting.

Running tests:

```bash
./gradlew test                     # unit tests (domain logic) — no device needed
./gradlew connectedDebugAndroidTest  # instrumented tests (Room) — needs a device/emulator
```

## What's implemented

- **Почетна** — today's lunch with a change button, ingredient/low-stock summary, 3–5 suggested recipes ranked by what you can cook, next week's plan status, quick-add shortcuts.
- **Намирници** — full inventory CRUD, search, category filter, sort (name/category/expiration), low-stock and expiring-soon filters, +/− quantity buttons, mark-finished, duplicate detection that offers to merge quantities instead of creating a second entry.
- **Рецепти** — recipe list with the four spec'd filters (can-cook-now / almost-ready / needs-shopping / favorites), search, category/time filters, full add/edit form (steps, ingredient lines, tags, notes), duplicate-to-variation, recipe detail with live ingredient coverage, serving-count adjustment with proportional scaling, one-tap "add missing ingredients to shopping list", and a "Готвев го ова" confirmation that shows exactly what will be deducted before touching inventory (and never deducts below zero).
- **Планер** — Monday–Sunday grid, week navigation, per-day readiness ("Може да се подготви веднаш" / missing count), copy-meal-to-another-day, editable household serving count, "Генерирај листа за пазарење" that nets requirements against inventory and never silently guesses when units conflict (it flags them instead).
- **Пазарење** — manual add, quantity/unit/category, purchased checkbox with separate purchased/unpurchased sections grouped by category, duplicate-combining, "Додади во домашни намирници" on purchase (with an explicit "no, bought for today" option that leaves inventory untouched), clear-purchased.
- **Sunday reminder** — a self-rescheduling WorkManager job fires at the time you set in Поставки, with a POST_NOTIFICATIONS request on first launch (Android 13+) and a manual "Генерирај листа за пазарење" button that works with notifications off.
- **Поставки** (reached from the gear icon on Почетна, not a 6th bottom-nav tab, per the 5-tab spec) — default household servings, reminder on/off and time, and JSON export (shares a `.json` file via the system share sheet) / import (file picker → validates → shows a summary → asks to confirm before overwriting anything).
- **Sample data** — 15 realistic Macedonian lunch recipes seeded on first run (тавче гравче, мусака, полнети пиперки, пилешко со ориз, ќофтиња, сарма, леќа чорба, and more); you add your own 50–100 from there, no code editing needed.

## Design decisions worth knowing about

- **Unit handling** (`domain/unit/UnitConverter.kt`): grams↔kilograms and milliliters↔liters convert freely; парче/пакување/конзерва are each only ever compatible with themselves. Nothing ever silently equates, say, 2 парче with 300 g — mismatches are surfaced to you (as a `UNIT_MISMATCH` in recipe matching, or a flagged conflict when generating the shopping list) rather than guessed at.
- **Recipe ingredients are matched by name, not by a foreign key** to the inventory table. A recipe can require "кромид" without "кромид" existing in your inventory yet — matching happens by normalized (trimmed, lower-cased) name at read time. This also means renaming an inventory item never silently breaks a recipe.
- **All core calculations are plain Kotlin with no Android dependency** (`domain/usecase/*`): unit conversion, recipe-vs-inventory matching, serving scaling, weekly shopping-list generation, week/Sunday date math. That's what the unit tests in `app/src/test/` exercise directly, no emulator required. A couple of behaviors that only make sense against a real database (negative-quantity prevention on cook, duplicate-free shopping-list regeneration) are instrumented tests in `app/src/androidTest/` instead.
- **No DI framework** — `di/AppContainer.kt` is a half-dozen-line hand-wired container. Hilt/Koin would be the obvious upgrade if the app grows, but felt like unnecessary ceremony (and one more dependency-version risk I couldn't verify compiles here) for a single-user app.
- **Room entities double as the JSON export/import schema** (`data/BackupData.kt`) via `kotlinx.serialization` — one less set of mapping code to maintain, at the cost of the export format being tied to the internal schema. Fine for a personal backup file; would need a dedicated DTO layer if this ever became a shared/public format.

## If something doesn't compile

The most likely failure points, ranked by probability, and how to fix them:

1. **A dependency version doesn't resolve or conflicts.** Open `gradle/libs.versions.toml` — bump the one Android Studio complains about to whatever it suggests.
2. **`menuAnchor()` deprecation warning** on the dropdown fields (category/unit pickers) — this is just a warning in Compose Material3 1.3.x (the no-arg overload was deprecated in favor of one taking a `MenuAnchorType`), not a build error. Safe to ignore, or accept Android Studio's quick-fix.
3. **KSP/Room compiler version mismatch** — the `ksp` version in the version catalog (`2.0.21-1.0.28`) must match your Kotlin version's KSP release; if Studio flags it, match it to whatever KSP version it recommends for Kotlin 2.0.21.

Send me the exact error and I'll patch it directly.
