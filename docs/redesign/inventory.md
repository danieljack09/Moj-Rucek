# Мој Ручек UI inventory (before the redesign)

Snapshot of every screen, dialog and interactive element under `app/src/main/java/com/mojrucek/app/ui/`,
taken on 2026-11-25 on branch `feat/shopping-cards-partial-purchase` before any redesign code changed.
This is the checklist used at the end to prove nothing was lost: every row here must still exist,
show the same text, and call the same callback after the restyle.

Conventions:
- `vm.x()` means a call on that screen's ViewModel. `onX` means a parameter callback of the screen composable.
- Strings are copied exactly, including `…`, `„“`, `→`, `—` and emoji. `$x` marks a value filled in at run time.
- "Ref" is the reference image in `docs/redesign/reference/` the screen maps to.
- Strings that come from outside `ui/` (domain `*Texts` objects or ViewModels) are listed where they appear,
  with their source, because the redesign must keep showing them but must not edit them.

---

## 0. App shell

### 0.1 AuthGate (`auth/AuthGate.kt`)
Signature: `AuthGate(container: AppContainer)`. Picks one of three targets via `authGateTarget(available, signedIn, BuildConfig.ALLOW_UNCONFIGURED_AUTH)`:
- `APP` → `MojRucekNavHost(container)` + `LegacyDataPrompt(container)`.
- `WELCOME` → `WelcomeScreen(AccountViewModel(...))`.
- `CONFIG_ERROR` → private `ConfigErrorScreen()`.

### 0.2 ConfigErrorScreen (the "not configured" screen, private in AuthGate.kt)
Ref: 04 (centred layout, same visual language as Welcome).
- Interactive elements: **none, on purpose** (no button, no retry). Must stay fully blocking.
- States: only one.
- Strings: `⚠️` (displayLarge emoji) · `Апликацијата не е правилно подесена` · `Оваа верзија нема конфигурација за сметки и не може да се користи. Инсталирај ја официјалната верзија на апликацијата.`

### 0.3 MojRucekNavHost (`navigation/MojRucekNavHost.kt`, `navigation/Destinations.kt`)
Ref: 10 (nav bar) and spec component 1 (floating nav bar).
- Global overlays shown on every in-app route: `GuestExpiredDialog(container, onOpenSettings = navigate(Settings))`, `SyncConflictDialog(container)`.
- Bottom bar, shown only when the current route is one of the five tabs (hidden on Settings, RecipeDetail, RecipeNew, RecipeEdit). Today: `Surface` with 20dp top corners, 8dp shadow, `surfaceVariant`, M3 `NavigationBar` with visible 10sp labels.
  | Tab | Route | Icon | Label / contentDescription |
  | --- | --- | --- | --- |
  | Home | `home` | `Icons.Filled.Home` | `Почетна` |
  | Ingredients | `ingredients` | `Icons.Filled.Kitchen` | `Намирници` |
  | Recipes | `recipes` | `Icons.Filled.MenuBook` | `Рецепти` |
  | Planner | `planner` | `Icons.Filled.CalendarMonth` | `Планер` |
  | Shopping | `shopping` | `Icons.Filled.ShoppingCart` | `Пазарење` |
  Tap → `navController.navigate(route) { popUpTo(start){saveState=true}; launchSingleTop=true; restoreState=true }`.
- Routes (must all survive): `home`, `ingredients`, `recipes`, `planner`, `shopping`, `settings`, `recipe/{recipeId}`, `recipe-edit-new`, `recipe-edit/{recipeId}`. Start destination `home`.
- Navigation wiring kept as is:
  - Home `onOpenLowStock` sets one-shot `showLowStockOnly`, which on Ingredients calls `vm.setLowStockOnly(true)`.
  - Home `onOpenNextWeekPlanner` sets one-shot `showNextWeekInPlanner`, which on Planner calls `vm.goToThisWeek(); vm.goToNextWeek()`.
  - RecipeNew `onSaved(id)` → navigate to `recipe/$id` popping up to `recipes`. RecipeEdit `onSaved` → `popBackStack()`.
  - RecipeDetail `onDuplicated(id)` → navigate to `recipe/$id` popping up to `recipes`; `onOpenShopping` → same as Shopping tab.
  - Settings receives `accountCard = { AccountCard(accountVm, guestLine = guestSettingsLine(container)); SyncCard(container); DeleteAccountSection(container) }`.
- Note for the redesign: tab screens get padding from this Scaffold's `innerPadding`. A floating nav bar will need list bottom padding (`Spacing.navBarClearance*`) on each tab screen instead.

---

## 1. WelcomeScreen (`auth/WelcomeScreen.kt`)
Signature: `WelcomeScreen(viewModel: AccountViewModel)`. Ref: **04** (sign-in screen: big title, pill buttons).

Interactive elements:
| Element | Action |
| --- | --- |
| Button `Продолжи со Google` (52dp, full width) | `viewModel.continueWithGoogle(context)`; disabled while `state.busy` |
| OutlinedButton `Продолжи со е-пошта` | sets `showEmail = true` → opens `EmailAuthDialog(viewModel, isGuest = false, onDismiss)`; disabled while busy |
| OutlinedButton `Продолжи како гостин` | `viewModel.continueAsGuest()`; disabled while busy |

States: idle · busy (all three buttons disabled + 32dp `CircularProgressIndicator` below) · message (`AccountMessageDialog(state.message, viewModel::dismissMessage)`) · email dialog open.

Strings: `🍲` (displayLarge) · app name from `R.string.app_name` · `Планирај ручеци, следи ги намирниците и подготви листа за пазарење.` · the three button labels · `Како гостин можеш веднаш да почнеш. Подоцна можеш да поврзеш Google сметка без да ги изгубиш податоците.`

## 2. EmailAuthDialog (`auth/EmailAuthDialog.kt`)
Signature: `EmailAuthDialog(viewModel: AccountViewModel, isGuest: Boolean, onDismiss: () -> Unit)`. Used from WelcomeScreen and AccountCard. Ref: **04** (fields, error state, forgot password link).

Interactive elements:
| Element | Action |
| --- | --- |
| Text field `Е-пошта` (email keyboard) | sets email, clears problem/info; disabled while busy |
| Text field `Лозинка (најмалку 8 знаци)` (CREATE) / `Лозинка` (SIGN_IN), password masked | sets password, clears problem/info; disabled while busy. **No eye toggle exists today** — do not add one (behaviour change) unless approved |
| TextButton `Веќе имам сметка` (CREATE) / `Нова сметка` (SIGN_IN) | toggles mode, clears messages |
| TextButton `Заборавена лозинка?` (SIGN_IN only) | `viewModel.requestPasswordReset(email) { err -> … }`; on success info = `Ако за оваа е-пошта постои сметка, испративме порака за нова лозинка.` |
| Confirm `Креирај` / `Најави се` | `viewModel.submitEmail(mode, email, password) { err -> … }`; success → `onDismiss()` |
| Dismiss `Откажи` | `onDismiss()`; disabled while busy |
| Outside tap / back | `onDismiss()` only when not busy |

States: CREATE mode · SIGN_IN mode · busy (everything disabled) · problem (error text in `colorScheme.error`, bodySmall) · info (bodySmall) · guest variant (extra line on top).

Strings: titles `Креирај сметка со е-пошта` / `Најави се со е-пошта` · guest line CREATE `Твоите податоци остануваат исти.` · guest line SIGN_IN `Ако се најавиш во постоечка сметка, прво ќе те прашаме што да правиме со податоците на овој телефон.` · plus the labels above. Problem texts come from `AccountViewModel` / EmailInput.

## 3. GuestPeriodUi (`auth/GuestPeriodUi.kt`)
- `guestPeriodStatus(container)` and `guestSettingsLine(container)`: logic only, no UI. The line is shown in AccountCard: from `GuestTexts.settingsLine` → `Гостин` / `Гостин · уште $n ден(а)` / `Гостин · гостинскиот период заврши`.
- **GuestExpiredDialog** `(container, onOpenSettings)`: Ref: dialog per spec (12).
  | Element | Action |
  | --- | --- |
  | Confirm `Поврзи со Google` | `dismissedForNow = true; onOpenSettings()` |
  | Dismiss `Подоцна` | `dismissedForNow = true` |
  | Outside tap / back | `dismissedForNow = true` |
  States: hidden · shown (at most once a day per uid; marks shown via `sessionRepository.markExpiredDialogShown`).
  Strings: `GuestTexts.EXPIRED_TITLE` = `Гостинскиот период заврши`; `GuestTexts.EXPIRED_TEXT` = `Сè уште можеш да користиш сè како досега. Поврзи Google сметка за да ја задржиш сметката.`

## 4. LegacyDataPrompt (`auth/LegacyDataPrompt.kt`)
`LegacyDataPrompt(container)` (logic) → `LegacyDataPromptDialog(message, onKeep, onLater, onSaveCopy)` (public, tested on its own; keep this signature). Ref: dialog per spec (12).
| Element | Action |
| --- | --- |
| OutlinedButton `Зачувај копија прво` (inside text) | `onSaveCopy` → system CreateDocument picker with `suggestedBackupFileName()`, then `saver.save(uri)` |
| Confirm `Задржи во оваа сметка` | `onKeep` → `ownershipCoordinator.keep(currentOwner.key)` |
| Dismiss `Подоцна` | `onLater` → `ownershipCoordinator.askLater()` |
| Outside tap / back | `onLater` |

States: hidden (`promptNeeded == false`) · shown · shown + save message (saved message from `savedBackupMessage(...)`, or `Копијата НЕ е зачувана.\n\n$reason`; cancelled → none).
Strings: title `Податоци од пред сметките` · body `На овој телефон има податоци создадени пред да постојат сметките (намирници, рецепти, листа за пазарење). Дали да ги задржиш во оваа сметка?\n\nАко избереш „Подоцна“, податоците остануваат на телефонот, но не се прикажуваат додека не одлучиш. Ништо не се брише.`

## 5. Home (`home/HomeScreen.kt`, `home/AdBannerSlot.kt`)
Signature (keep): `HomeScreen(viewModel: HomeViewModel, onOpenSettings, onOpenRecipe: (Long) -> Unit, onOpenPlanner, onOpenLowStock, onOpenShopping, onOpenNextWeekPlanner, adBanner: (@Composable () -> Unit)? = null)`.
Ref: **01** and **06** (main page). The reference's search pill, recipe grid and promo card have no matching Home function, except: the promo card style fits the Weekly reminder card and the Today's lunch card (both have a CTA).

Interactive elements:
| Element | Action |
| --- | --- |
| Top bar IconButton Settings (cd `Поставки`) | `onOpenSettings()` |
| Today's lunch: Button `Избери јадење` (no meal) / `Промени` (meal set) | `vm.recipesForPicker { pickerRecipes = it }` + `showRecipePicker = true` → RecipePickerDialog |
| Today's lunch: TextButton `Отвори рецепт` (only when meal set) | `onOpenRecipe(today.recipeId)` |
| Week preview: 7 day cells (clickable Cards) | meal set → `onOpenRecipe(meal.recipeId)`; empty → `onOpenPlanner()` |
| Kitchen overview card `Намирници при крај` (clickable) | `onOpenLowStock()` |
| Kitchen overview card `За купување` (clickable) | `onOpenShopping()` |
| Weekly reminder card (whole card clickable) | `onOpenNextWeekPlanner()` |
| AdBannerSlot | renders `adBanner` only when non-null (and only then adds a list item) |

States:
- Loading (`state.isLoading`) → full-screen `CircularProgressIndicator`.
- Today: no meal (`Нема избрано јадење за денес.`) / meal set (recipe name).
- Week cells: today (highlighted, 2dp border) / planned (recipe name, 2 lines) / empty (`+`).
- Counts: low stock count, unpurchased shopping count (may be 0).
- Weekly reminder: 0 planned / 1–6 planned / 7 planned (different message and action text).
- Ad slot: absent / present.

Strings: top bar `Мој Ручек` · `Што ќе готвиме денес?` · `🍽️ Денешен ручек` · `Нема избрано јадење за денес.` · `Избери јадење` · `Промени` · `Отвори рецепт` · `Оваа недела` · day labels `ПОН` `ВТО` `СРЕ` `ЧЕТ` `ПЕТ` `САБ` `НЕД` · `+` · `Кујнски преглед` · `Намирници при крај` · `За купување` · `Неделен потсетник` · messages `Време е да го испланираш ручекот за следната недела.` / `Следната недела има испланирани $n од 7 ручеци.` / `Ручекот за следната недела е целосно испланиран.` · actions `Планирај ја следната недела →` / `Прегледај ја следната недела →`.

Hard-coded colours today (to be replaced by tokens): WeekPreview `0xFFE8E2EB`, `0xFFD6E3D2`, `0xFFF8F5ED`, `0xFFDDE6D6`, `0xFF344B3B`, `0xFF35342F`; OverviewCards `0xFFDDE6D6`/`0xFF344B3B` and `0xFFE8E2EB`/`0xFF35342F`; reminder `0xFFF0E5C9`/`0xFF67542F`.

### 5.1 RecipePickerDialog (private, HomeScreen.kt)
Ref: bottom sheet or dialog per spec (11/12), search field per spec (4).
| Element | Action |
| --- | --- |
| Search field, placeholder `Пребарај рецепт`, leading search icon | local `query` filter (MacedonianSearch.normalizeForSearch) |
| Each recipe row (TextButton, full width) | `onPick(recipeId)` → `vm.setTodayMeal(id)` and close |
| Confirm `Затвори` | `onDismiss` |
| Dismiss `Испразни ден` | `onClear` → `vm.setTodayMeal(null)` and close |
| Outside tap / back | `onDismiss` |
States: list with favourites first; favourites + others → `Омилени` label and a divider between groups; only one group → no label; no matches → `Нема пронајдени рецепти`. Favourite rows show a star (cd `Омилен рецепт`), others an empty spacer.
Strings: title `Избери рецепт за денес` + above.

## 6. Ingredients (`ingredients/IngredientsScreen.kt`)
Signature (keep): `IngredientsScreen(viewModel: IngredientsViewModel)`.
Ref: **09** (grocery list rows) with **06** chips.

Interactive elements:
| Element | Action |
| --- | --- |
| Top bar IconButton Sort (cd `Подреди`) | opens sort DropdownMenu |
| Sort menu `Име` / `Категорија` / `Количина` / `Рок на траење` | `vm.setSort(IngredientSort.NAME / CATEGORY / QUANTITY / EXPIRATION)` and close |
| FAB Add (cd `Додади намирница`) | `showAddDialog = true` → IngredientFormDialog(initial = null) |
| CategoryChipsRow: `Сите ($total)` + one chip per category `🥕 $name ($count)` | `vm.setCategoryFilter(category or null)` |
| Search field label `Пребарај` | `vm.setSearchQuery(text)` |
| FilterChip `При крај` | `vm.setLowStockOnly(!state.lowStockOnly)` |
| FilterChip `Истекува наскоро` | `vm.setExpiringOnly(!state.expiringOnly)` |
| Row tap (whole card) | `editingIngredient = ingredient` → IngredientFormDialog(initial = ingredient) |
| Row IconButton `Намали` (−) | `vm.adjustQuantity(ingredient, -stepFor(unit))` (kg/l 0.1, g/ml 50, count 1) |
| Row IconButton `Зголеми` (+) | `vm.adjustQuantity(ingredient, +stepFor(unit))` |
| Row IconButton `Повеќе опции` (⋮) → menu `Уреди` | opens the edit dialog |
| Row menu `Избриши` (with delete icon) | `vm.deleteIngredient(ingredient)` (no confirm, no undo) |

States: empty inventory (`Нема намирници. Додади ја првата од копчето +.`) · filter has no match (`Нема намирници што одговараат на филтерот.`) · list · **low stock** (category·quantity line in `colorScheme.error`) · **expiry label**: `Истечено` / `Истекува денес` / `Истекува утре` / `Истекува за $n дена` (≤3 days, shown in error colour) / `Рок: dd.MM.yyyy` (normal colour) / none · duplicate pending (dialog 6.3) · add dialog · edit dialog.
Row text: name (bodyLarge) · `$categoryName · $qty $unit` · optional expiry label. Leading `CategoryEmojiBadge` (category emoji in a primaryContainer circle).

### 6.1 IngredientFormDialog (private)
Ref: bottom sheet per spec (11, like 09 edit sheet), fields per spec (5).
| Element | Action |
| --- | --- |
| Field `Име` | sets name, re-enables suggestions |
| SuggestionList (up to 5 rows, emoji + name; add mode only until a pick) | sets name, and category/unit when known; hides suggestions |
| Category preview: `CategoryEmojiBadge` (32dp) + category name | display only |
| CategoryChipsRow (no `Сите`) | sets `category` (null → `DRUGO`) |
| Field `Количина` | quantity text (must parse, ≥ 0) |
| Exposed dropdown `Единица` → one item per `MeasurementUnit` | sets unit |
| ExpirationDateField `Рок на траење (опционално)`, placeholder `Избери датум` | press opens DatePickerDialog; trailing IconButton `Отстрани датум` (Clear) when a date is set, else plain Event icon |
| Field `Праг за „при крај“ (опционално)` | low-stock threshold |
| Field `Белешки (опционално)` (multi-line) | notes |
| Confirm `Зачувај` (enabled only if name not blank and quantity valid) | add: `vm.submitNewIngredient(input)`; edit: `vm.updateIngredient(ingredient.copy(...))` |
| Dismiss `Откажи` / outside tap | close |
States: add (`Нова намирница`) vs edit (`Уреди намирница`) · invalid (Save disabled) · suggestions shown/hidden · date set / not set.
Labels also: `Категорија`.

### 6.2 Expiration DatePickerDialog (inside 6.1)
Confirm `Во ред` → sets epoch day from the selected date; Dismiss `Откажи`. M3 DatePicker body.

### 6.3 Duplicate ingredient dialog (inline AlertDialog)
Shown when `vm.pendingDuplicate != null`.
| Element | Action |
| --- | --- |
| Confirm `Ажурирај количина` | `vm.confirmMergeDuplicate()` |
| Dismiss `Додади посебно` | `vm.confirmAddAsSeparateEntry()` |
| Outside tap | `vm.dismissDuplicateDialog()` |
Strings: title `Намирницата веќе постои` · text `„$name“ веќе ја имаш дома ($qty $unit). Да ја зголемам количината наместо да креирам нова ставка?`

## 7. Recipes (`recipes/RecipesScreen.kt`)
Signature (keep): `RecipesScreen(viewModel: RecipesViewModel, onOpenRecipe: (Long) -> Unit, onAddRecipe: () -> Unit)`.
Ref: **06** (grid, search, filter and sort sheets). The app has no sort for recipes and no filter sheet today; filters are chip rows. Moving the chips into a sheet would be a behaviour change, so keep them as chips (spec 6) unless approved.

Interactive elements:
| Element | Action |
| --- | --- |
| FAB Add (cd `Нов рецепт`) | `onAddRecipe()` |
| Search field label `Пребарај рецепти` | `vm.setSearchQuery(text)` |
| Readiness chips (single choice): `Сите` / `Можам да зготвам сега` / `Речиси подготвено` / `Треба да купам намирници` | `vm.setReadinessFilter(ALL / CAN_COOK_NOW / ALMOST_READY / NEEDS_SHOPPING)` |
| FilterChip `Омилени` (toggle, same row) | `vm.setFavoritesOnly(!state.favoritesOnly)` |
| Time chips (single choice): `Секое време` / `До 30 мин` / `До 60 мин` | `vm.setMaxTime(null / 30 / 60)` |
| Recipe card tap | `onOpenRecipe(recipeId)` |
| Card IconButton heart (cd `Омилено`) | `vm.toggleFavorite(recipeId, !isFavorite)` |

States: empty / no match (`Нема рецепти што одговараат на филтрите.`, one message for both) · list · favourite / not (filled heart in error colour vs outline) · readiness colour of meta line (CAN_COOK_NOW primary, ALMOST_READY secondary, NEEDS_SHOPPING onSurfaceVariant).
Card text: name · meta `$category` (or `Без категорија`) + ` · $n мин` (only if > 0) + ` · Покриени: $covered/$total`.
Data available for the grid card: `match.recipeName`, `isFavorite`, readiness, covered/total, `item.category`, `item.totalTimeMinutes`. `Recipe.imageUri` exists in the entity but is not in `RecipeListItem`, and no image loader is a dependency (see RecipeCover below), so cards show the category-icon fallback (spec 7).

## 8. RecipeDetail (`recipes/RecipeDetailScreen.kt`)
Signature (keep): `RecipeDetailScreen(viewModel: RecipeDetailViewModel, onBack, onEdit: (Long) -> Unit, onDuplicated: (Long) -> Unit, onOpenShopping)`.
Ref: **07** (recipe page, cooking steps). Ratings, reviews, rate sheet, author and nutrition have no match → leave out.

Interactive elements:
| Element | Action |
| --- | --- |
| Nav IconButton back (cd `Назад`) | `onBack()` |
| Action heart (cd `Омилено`) | `vm.toggleFavorite()` (only when recipe loaded) |
| Action edit (cd `Уреди`) | `onEdit(recipe.id)` |
| Action copy (cd `Копирај`) | `vm.duplicate(onDuplicated)` |
| Action delete (cd `Избриши`) | `showDeleteConfirm = true` → delete dialog |
| Servings stepper − (cd `Намали порции`) / + (cd `Зголеми порции`) | `vm.setDesiredServings(n-1 coerceAtLeast 1 / n+1)` |
| Button (shopping-cart icon) `Додади ги сите намирници што недостасуваат во листата за пазарење` (only if missing items) | `vm.addMissingToShoppingList()` |
| OutlinedButton `Оди во листата за пазарење` (only after adding, `rememberSaveable`) | `onOpenShopping()` |
| Button `Го зготвив ова` | `showCookConfirm = true` → cook dialog |
| Snackbar action `Кон пазарење` | `onOpenShopping()` |

States: loading (spinner) · not found (`Рецептот не постои.`) · loaded · access Own / Unlocked (description, steps, notes shown) / **Locked** (card with `LimitTexts.LOCKED_TITLE` = `Чекорите се заклучени` and `LimitTexts.LOCKED_TEXT` = `Целосниот рецепт (опис, чекори, белешки) се отвора кога ќе направиш листа за пазарење со овој рецепт.`; no description, steps or notes) · has missing / none missing · added to shopping list (snackbar + extra button) · just cooked (snackbar) · ingredient row available (quantity in primary) / not (error colour).

Strings: title = recipe name or `Рецепт` · `Подготовка: $p мин · Готвење: $c мин` · `Порции:` · `Покриени намирници: $covered од $total` · `Недостигаат:` · bullet rows `• $name — $qty $unit` / `• $name — уште $qty $unit` / `• $name — единицата во инвентарот не одговара` · `Потребни намирници` · ingredient rows name + `$qty $unit` · `Чекори` · steps `$i. $step` · `Белешки` · snackbars `Сите намирници што недостасуваат се додадени во листата за пазарење.` (action `Кон пазарење`, long) and `Намирниците се одземени од инвентарот.`

### 8.1 Cook confirm dialog
Confirm `Потврди` → `vm.confirmCooked()`; Dismiss `Откажи`. Title `Да ги одземам намирниците?`; text `Ќе се одземат следниве количини од инвентарот (за $n порции):` + `• $name: $qty $unit` per ingredient.

### 8.2 Delete recipe dialog
Confirm `Избриши` → `vm.delete(onBack)`; Dismiss `Откажи`. Title `Избриши рецепт?`; text `Ова не може да се врати.`

## 9. RecipeEdit (`recipes/RecipeEditScreen.kt`)
Signature (keep): `RecipeEditScreen(viewModel: RecipeEditViewModel, onBack, onSaved: (Long) -> Unit)`. `LaunchedEffect(state.savedRecipeId)` calls `onSaved(id)`.
Ref: **11** (personal information form) with **04** field styles.

Interactive elements:
| Element | Action |
| --- | --- |
| Nav back (cd `Назад`) | `onBack()` |
| Field `Име на рецептот` | `vm.setName` |
| Field `Опис (опционално)` | `vm.setDescription` |
| Field `Порции` | `vm.setServingsText` |
| Field `Подготовка (мин)` | `vm.setPrepTimeText` |
| Field `Готвење (мин)` | `vm.setCookTimeText` |
| Dropdown `Категорија`: `Без категорија` + each `RecipeCategory` | `vm.setCategory(null / c)` |
| Field `Ознаки, одделени со запирка (опционално)` | `vm.setTagsText` |
| Per ingredient line: field `Намирница` | `vm.updateIngredientLine(id) { line.copy(name) }` |
| Per ingredient line: IconButton delete (cd `Избриши намирница`) | `vm.removeIngredientLine(id)` |
| Per ingredient line: field `Количина` | `updateIngredientLine(... quantityText)` |
| Per ingredient line: dropdown `Единица` (each MeasurementUnit) | `updateIngredientLine(... unit)` |
| OutlinedButton `+ Додади намирница` | `vm.addIngredientLine()` |
| Per step: field `Чекор $n` | `vm.setStep(index, text)` |
| Per step: IconButton delete (cd `Избриши чекор`) | `vm.removeStep(index)` |
| OutlinedButton `+ Додади чекор` | `vm.addStep()` |
| Field `Белешки (опционално)` | `vm.setNotes` |
| Button `Зачувај рецепт` | `vm.save()`; enabled only when `state.isValid` |

States: new (`Нов рецепт`) vs edit (`Уреди рецепт`) · invalid (save disabled) · 0..n ingredient lines · 0..n steps · saved (navigates away).
Section labels: `Намирници`, `Чекори на подготовка`.

## 10. Planner (`planner/PlannerScreen.kt`)
Signature (keep): `PlannerScreen(viewModel: PlannerViewModel, onOpenRecipe: (Long) -> Unit, onOpenSettings: () -> Unit = {})`.
Ref: no direct reference; use the **10** design system.

Interactive elements:
| Element | Action |
| --- | --- |
| IconButton prev (cd `Претходна недела`) | `vm.goToPreviousWeek()` |
| TextButton with the range `dd.MM.yyyy — dd.MM.yyyy` | `vm.goToThisWeek()` (tapping the date returns to this week) |
| IconButton next (cd `Следна недела`) | `vm.goToNextWeek()` |
| Household servings − (cd `Намали`) / + (cd `Зголеми`) | `vm.setHouseholdServings(n-1 coerceAtLeast 1 / n+1)` |
| Day card tap | meal set → `onOpenRecipe(recipeId)`; empty → open picker |
| Day card TextButton `Избери` (empty) / `Промени` (set) | `vm.recipesForPicker { … }` + `dayBeingEdited = day` → DayRecipePickerDialog |
| Button `Генерирај листа за пазарење` / `Регенерирај листа за пазарење` | `vm.generateShoppingList()`; disabled while `state.generating` |

States: incomplete days warning (`Сè уште нема јадење за: $days` in error colour, day names from `dayName()` `Понеделник`…`Недела`) · day planned + can prepare now (`Може да се подготви веднаш`, primary) · day planned + missing (`Недостигаат $n намирници`, error) · day empty (`Нема избрано јадење`) · next list line (from `LimitTexts.nextListLine` = `Следна листа може за $countdown`) · generating (button disabled) · list generated before (Регенерирај label) · limit notice dialog · generation result dialog.
Day card text: `$dayName · dd.MM` · `$recipeName ($n порции)`.

### 10.1 DayRecipePickerDialog (private)
| Element | Action |
| --- | --- |
| `Копирај од друг ден:` row of TextButtons, 3-letter day names (only days with meals) | `onCopyFrom(fromDay)` → `vm.copyMeal(fromDay, day)` |
| Recipe rows (TextButton, full width, max 320dp list) | `onPick(id)` → `vm.setMeal(day, id)` |
| Confirm `Затвори` | close |
| Dismiss `Испразни ден` | `vm.setMeal(day, null)` |
Title `Избери рецепт`. No search here (unlike Home's picker) — do not add one.

### 10.2 Limit notice dialog (`state.limitNotice`)
Title/text from `LimitTexts.forResult`: `Нова листа уште не може` / `Следната листа може да се направи за $countdown. Рецептите од последната листа остануваат отворени до тогаш.` · `Гостинскиот период заврши` / `Поврзи Google сметка за да направиш нова листа. Постојната листа можеш и натаму да ја користиш и уредуваш.` · `Најмногу $max рецепти по листа` / `За една листа избери најмногу $max различни рецепти од серверот.` · `Потребна е интернет врска` / `За нова листа апликацијата мора да ја провери со серверот. Обиди се повторно кога ќе имаш врска.` · `Серверот не е достапен` / `Обиди се повторно за малку.` · `Не успеа` / server message.
Buttons: `linkGoogle` → Confirm `Поврзи со Google` (`vm.dismissLimitNotice(); onOpenSettings()`) + Dismiss `Подоцна`; otherwise Confirm `Во ред`. Outside tap → `vm.dismissLimitNotice()`.

### 10.3 Generation result dialog (`state.lastGenerationResult`)
Title `Листата за пазарење е подготвена`; `Додадени се $n ставки.`; with conflicts: `Внимание — овие намирници имаат несовпаѓање на единици и мора рачно да се проверат:` (error) + `• $name: $unit1, $unit2`. Confirm `Во ред` → `vm.clearGenerationResult()`.

## 11. Shopping (`shopping/ShoppingScreen.kt`)
Signature (keep): `ShoppingScreen(viewModel: ShoppingViewModel)`.
Ref: **09** (grocery list, select mode, edit sheet). The app has no select mode, no checkbox list and no edit sheet for list items; leave those out. Restyle the existing card + action bar in 09's row language.

Interactive elements:
| Element | Action |
| --- | --- |
| FAB Add (cd `Додади ставка`) | `showAddDialog = true` → AddShoppingItemDialog |
| Swipe right (StartToEnd) on a card | `vm.addToInventory(item, item.quantity)` (background: Home icon + `Во инвентар`, secondaryContainer) |
| Swipe left (EndToStart) | `vm.delete(item)` (background: Delete icon + `Избриши`, errorContainer) |
| Segment `Во инвентар` (Home icon), tap | `vm.addToInventory(item, item.quantity)` |
| Segment `Во инвентар`, **long-press** (a11y label `Делумно купено`) | PartialPurchaseDialog with `toInventory = true` |
| Segment `За сега` (Schedule icon), tap | `vm.keepWithoutAdding(item, item.quantity)` |
| Segment `За сега`, **long-press** (a11y label `Делумно купено`) | PartialPurchaseDialog with `toInventory = false` |
| Segment `Избриши` (Delete icon), tap | `vm.delete(item)` (no long-press) |
| Snackbar action `Врати` (short; `collectLatest` replaces the old one) | `vm.undo(event.undo)` |

States: empty (`Листата за пазарење е празна.`) · grouped list by category in store order with headers `$emoji $category ($count)` · item with source line (`од: $recipeName` / `од: неделниот план`) or without · item count in title (`$n ставка` for 1, 21, 31… except 11; otherwise `$n ставки`) or no subtitle when 0 · items animate in and out (`animateItem`) · swipe in progress · snackbar after each action · **partial purchase** dialog.
Snackbar messages (from ShoppingViewModel): `Во инвентар: $name` · `Купено: $name (единицата не одговара со инвентарот, не е додадено)` · `Купено: $name` · `Избришано: $name`, each optionally followed by ` · уште $qty $unit во листата`.
Card text: name (2 lines max) · source line · quantity pill `$qty $unit`.

### 11.1 PartialPurchaseDialog (private)
| Element | Action |
| --- | --- |
| Field `Количина ($unit)`, decimal keyboard, accepts comma, error when not > 0 | local text |
| AssistChip `Половина` | fills half the listed quantity |
| AssistChip `Сè` | fills the full listed quantity |
| Checkbox + `Додади во инвентар` | toggles `toInventory` (starts from the segment that was long-pressed) |
| Confirm `Потврди` (enabled only when valid) | quantity capped to the listed amount → `toInventory ? vm.addToInventory(item, q) : vm.keepWithoutAdding(item, q)` |
| Dismiss `Откажи` | close |
Strings: title `Купено: $name` · `На листата: $qty $unit`.
States: valid / invalid (field error, confirm disabled) · to inventory on / off.

### 11.2 AddShoppingItemDialog (private)
| Element | Action |
| --- | --- |
| Field `Ставка` | name |
| Field `Количина` (default `1`, must be > 0) | quantity |
| Dropdown `Единица` (each MeasurementUnit, default PIECE) | unit |
| Dropdown `Категорија` (each IngredientCategory, default DRUGO) | category |
| Confirm `Додади` (enabled when valid) | `vm.addManualItem(name.trim(), qty, unit, category)` |
| Dismiss `Откажи` | close |
Title `Додади во листа за пазарење`.

## 12. Settings (`settings/SettingsScreen.kt`)
Signature (keep): `SettingsScreen(viewModel: SettingsViewModel, onBack, accountCard: @Composable () -> Unit = {})`.
Ref: **11** (profile list). Subscription, notes and categories from 11 have no match → leave out.

Order on screen: accountCard() slot (AccountCard, SyncCard, DeleteAccountSection) → default servings → reminder → catalog → backup.

Interactive elements:
| Element | Action |
| --- | --- |
| Nav back (cd `Назад`) | `onBack()` |
| Default servings − / + (cd `Намали` / `Зголеми`) | `vm.setDefaultServings(n-1 / n+1)` |
| Switch next to `Потсетник за планирање` | `vm.setReminderEnabled(checked)` |
| Segmented `Неделно` / `Дневно` (animated colours) | `vm.setReminderMode(WEEKLY / DAILY)` |
| Day circles `По` `Вт` `Ср` `Че` `Пе` `Са` `Не` (animated scale + colour) | WEEKLY: `vm.setReminderWeeklyDay(day)` (single); DAILY: `vm.setReminderDays(days ± day)` (multi) |
| Hour stepper −/+ (cd `Намали` / `Зголеми`, wraps 0..23) | `vm.setReminderTime(h, m)` |
| Minute stepper −/+ (step 5, wraps 0..59) | `vm.setReminderTime(h, m)` |
| OutlinedButton `Синхронизирај` / `Се синхронизира…` | `vm.syncCatalog()`; disabled while `state.isSyncing` |
| Button `Зачувај на уредот` / `Се зачувува…` | CreateDocument picker (`suggestedBackupFileName()`) → `vm.saveBackupTo(uri)`; disabled while `state.isSaving` |
| OutlinedButton `Сподели копија` | `vm.exportData()` → `LaunchedEffect(exportedJson)` writes a cache file and opens chooser `Сподели резервна копија`, then `vm.clearExport()` |
| OutlinedButton `Вчитај од датотека` | GetContent picker (`application/json`) → `vm.validateImport(text)` → import dialog |

States: reminder off (only the switch row) · on + WEEKLY (single day) · on + DAILY (multi day) · syncing · saving · import confirm / invalid file · save message · sync message · import message.

Strings: `Поставки` · `Стандарден број на порции` · `Потсетник за планирање` · `Време на потсетник` · `:` · times as `%02d` · `Каталог` · `Листата на намирници и рецептите се преземаат од серверот. Синхронизирај за да ги добиеш најновите промени.` · `Резервна копија` · `Зачувај ги сите намирници, рецепти, планови и листи во датотека на телефонот, или сподели ја копијата. Подоцна можеш да ја вчиташ назад.` · `Ќе избереш каде да се зачува, на пример во Преземања или Документи.`

### 12.1 Import dialog
Title `Увоз на податоци`; text = `Ќе се вчитаат $r рецепти, $i намирници, $s ставки за пазарење. Ова ги заменува сите постоечки податоци. Продолжи?` or the invalid-file reason. Confirm `Продолжи` (only when text is valid) → `vm.importData(text)`; Dismiss `Откажи`.

### 12.2 Message dialogs
- `Резервна копија` + `state.saveMessage`, `Во ред` → `vm.clearSaveMessage()`.
- `Синхронизација` + `state.syncMessage`, `Во ред` → `vm.clearSyncMessage()`.
- `Готово` + `state.importMessage`, `Во ред` → `vm.clearImportMessage()`.

## 13. AccountCard (`auth/AccountCard.kt`)
Signature (keep): `AccountCard(viewModel: AccountViewModel, guestLine: String = "Гостин")`. Ref: **11** (profile header / list).
Hidden when accounts are not configured or nobody is signed in.

Interactive elements:
| Element | Action |
| --- | --- |
| Guest: Button `Поврзи со Google` | `viewModel.continueWithGoogle(context)`; disabled while busy |
| Guest: OutlinedButton `Поврзи со е-пошта` | opens `EmailAuthDialog(isGuest = true)` |
| Email unverified: OutlinedButton `Потврдив` | `viewModel.checkVerification()` |
| Email unverified: OutlinedButton `Испрати повторно` | `viewModel.resendVerification()` |
| Google/Email: OutlinedButton `Одјави се` | sign-out confirm dialog |

States: guest (with guestLine) · Google (`Google: $email`) · Email verified (`Е-пошта: $email`) · Email unverified (red text + two extra buttons) · other kind (`Сметка: $email`, `—` when no email) · busy (24dp spinner, buttons disabled) · switch flow dialogs · message dialog.
Strings: `Сметка` · `Поврзи Google сметка за да ја задржиш сметката. Твоите податоци остануваат исти.` · `Е-поштата не е потврдена. Отвори го линкот што ти го испративме. Додека не ја потврдиш, сметката важи како гостинска.`

### 13.1 Sign-out confirm dialog
Title `Одјава`; text `Ќе се одјавиш од сметката. Податоците на овој телефон остануваат.`; Confirm `Одјави се` → `viewModel.signOut()`; Dismiss `Откажи`.

### 13.2 SwitchAccountDialogs (private, guest → existing account)
- **Explain**: title `Сметката веќе постои`; text `${email ?: "Оваа Google сметка"} веќе е поврзана со друга сметка во Мој Ручек. Сметките не се спојуваат.\n\nАко продолжиш:\n1. прво се прави и проверува копија од податоците на овој телефон;\n2. потоа се најавуваш во постоечката сметка;\n3. намирниците, твоите рецепти, плановите и листата за пазарење се бришат од овој телефон.\n\nГостинската сметка потоа не може да се врати. Ако откажеш, ништо не се менува.`; Confirm `Направи копија` → `viewModel.makeSnapshot()`; Dismiss `Откажи` → `viewModel.cancelSwitch()`.
- **MakingSnapshot / Switching**: non-dismissable; title `Се прави копија…` / `Префрлање…`; spinner; no buttons.
- **Confirm**: non-dismissable by back/outside; title `Копијата е готова`; text `Копијата е зачувана и проверена: $r рецепти, $i намирници, $s ставки за пазарење, $w планови.\n\nПрепорачуваме да ја зачуваш и надвор од телефонот. Подоцна можеш да ја вчиташ преку Поставки → Резервна копија.`; OutlinedButton `Зачувај на уредот` (CreateDocument → saver; message saved / `Копијата НЕ е зачувана на уредот.\n\n$reason`); OutlinedButton `Сподели копија` (chooser `Сподели копија`); checkbox row (whole row clickable) `Разбирам дека локалните податоци ќе се избришат од овој телефон.`; Confirm `Најави се и избриши` (enabled when ticked) → `viewModel.confirmSwitch()`; Dismiss `Откажи` → `viewModel.cancelSwitch()`.

### 13.3 AccountMessageDialog (public, also used by WelcomeScreen)
`AccountMessageDialog(message: String?, onDismiss)`: title `Сметка`, text = message, Confirm `Во ред`.

## 14. SyncUi (`sync/SyncUi.kt`)
### 14.1 SyncCard (in Settings, hidden when signed out or not configured)
| Element | Action |
| --- | --- |
| OutlinedButton `SyncTexts.BUTTON` = `Синхронизирај сега` | `syncService.requestSync(manual = true)`; disabled while syncing |
| Conflict line (clickable text) | `syncService.reopenChoice()` |
States and strings: title `Синхронизација` · `Последна синхронизација: $when` (`пред малку` / `пред $n мин` / `пред $n ч` / `dd.MM.yyyy HH:mm`) or `Сè уште не е синхронизирано.` · Idle (nothing) · Syncing (18dp spinner + `Се синхронизира…`) · InSync `Сè е синхронизирано.` · NeedsChoice `Податоците се разликуваат. Допри за да избереш.` (error colour, clickable) · Offline `Нема интернет врска. Ќе се обидам повторно кога ќе има.` · TryLater `Серверот не е достапен. Ќе се обидам повторно.` · TooLarge `Податоците се преголеми за синхронизација.` (error) · Problem `$message` (error).

### 14.2 SyncConflictDialog (global, from NavHost)
Shown when status is NeedsChoice and not dismissed.
| Element | Action |
| --- | --- |
| `Задржи ги од овој телефон` | `syncService.chooseKeepThisPhone()` |
| `Користи ги од сервисот` | `syncService.chooseUseCloud()` |
| Dismiss `Подоцна` / outside tap | `syncService.dismissChoice()` |
Title `Податоците се разликуваат`; text `Овој телефон и сервисот имаат различни промени. Избери која верзија да се задржи. Пред да се замени нешто на телефонот, се зачувува копија од неговите податоци.` The two choice buttons are stacked in a Column, right-aligned.

## 15. DeleteAccountSection (`auth/DeleteAccountSection.kt`)
`DeleteAccountSection(container)`; hidden unless signed in as GUEST, GOOGLE or EMAIL. Also defines `DeleteAccountViewModel` and `DeleteStage` in the same file — **these are logic, not UI: leave them untouched.**
| Element | Action |
| --- | --- |
| TextButton `Избриши сметка` (error colour, full width) | `vm.open()` → Confirm stage |
| Confirm dialog: OutlinedButton `Зачувај копија прво` | CreateDocument → saver; message saved / `Копијата НЕ е зачувана.\n\n$reason` |
| Confirm dialog: field `Лозинка (за потврда)` (EMAIL only, masked) | password |
| Confirm dialog: checkbox row (whole row clickable) `Разбирам дека не може да се врати` | toggles understood |
| Confirm `Избриши` (enabled when understood and, for email, password not empty; error colour when ready, outline colour when not) | `vm.confirm(context, password?)` |
| Dismiss `Откажи` / outside | `vm.close()` |
| Problem dialog `Во ред` / outside | `vm.close()` |
States: Idle · Confirm (title `Избриши сметка`, text `Ќе се избришат сметката и сите нејзини податоци на овој телефон (намирници, твоите рецепти, планови, листа за пазарење, омилени), како и записот на нашиот сервер.\n\nОва не може да се врати.`) · Working (non-dismissable, `Се брише…` + spinner) · Problem (`Не успеа` + message).

## 16. AdBannerSlot (`home/AdBannerSlot.kt`)
`AdBannerSlot(modifier, content: (@Composable () -> Unit)? = null)`. No interaction, no text. Null content → emits nothing (zero height). Present → full-width centred box. Ref: none; keep it invisible when empty.

## 17. Shared components already in use (`components/CommonComponents.kt`, `BackupSave.kt`, `ViewModelFactory.kt`)
- `EmptyState(message, actionLabel?, onAction?, modifier)`: 72dp primaryContainer circle with Info icon, message, optional Button. Used by Ingredients, Recipes, Shopping (no action label passed anywhere today).
- `formatQuantityWithUnit(q, unit)`: text helper, keep.
- `CategoryEmojiBadge(category, size = 40.dp)`: category emoji in a primaryContainer circle. Used by Ingredients row and form.
- `CategoryChipsRow(categories, selected, onSelect, modifier, showAllOption, counts, totalCount)` + private `CategoryChip` (FilterChip with emoji). Strings `Сите` / `Сите ($n)` / `$category ($n)`.
- `BackupSave.kt`: `suggestedBackupFileName`, `backupSaverFor`, `savedBackupMessage` — logic, no visuals; leave untouched.
- `ViewModelFactory.kt` (`GenericViewModelFactory`) — logic; leave untouched.

## 18. Theme today (`theme/`)
- `Color.kt` + `Theme.kt`: old sage/terracotta palette (primary DeepGreen `#405B45`, secondary Terracotta, background `#FAF7F0`, surface white). Dark scheme exists. No dynamic colour. Sets light status-bar icons via `WindowCompat`. **Does not match the spec** (spec: accent `#F16F33`, green `#ACBD6F`, background `#FFFAE7`, …).
- `Type.kt`: only five styles overridden, SemiBold titles, default font. **Does not match the spec** (Inter, Medium titles, full scale).
- `Shape.kt`: 8/12/16/20/28dp. Partly fits (cards 20, dialogs/sheets 28) but pills need `RoundedCornerShape(50)` explicitly.
- `res/values/themes.xml`: `Theme.MojRucek` parent `android:Theme.Material.Light.NoActionBar`. `res/font/`: `playfair_display.ttf`, `playfair_display_italic.ttf` (spec says Playfair is not used; Inter is missing). `licenses/PlayfairDisplay-OFL.txt` exists; an Inter OFL licence file will be needed.

---

## 19. Untracked redesign files (started earlier, not wired up)

None of these is referenced from any screen today. Usage below counts only references from outside the file itself.

| File | Used? | Fits the spec? |
| --- | --- | --- |
| `components/ArcMeter.kt` (76dp 270° progress arc with value + label, clickable, a11y "label: value") | No | **No direct spec component.** The reference has no arc meters. Could restyle Home's kitchen overview, but the spec's Home language is cards and pills. Drop or leave unused. |
| `components/CategoryIcons.kt` (`IngredientCategory.icon()` → Material icon per category) | No | **Yes.** Spec 7 ("category icon on a green circle") and spec 14 (outlined icons) want icons, not emoji. Should switch from `Icons.Filled.*` to `Icons.Outlined/Rounded.*` to match "thin stroke". Emoji stays in data. |
| `components/CircleIconButton.kt` (44dp surface circle, 1dp outline, 20dp icon, 48dp target) | Only by ScreenHeader (also unused) | **Partly.** Spec 3: 40dp **greenContainer** circle, **no outline**, **darkOlive** icon. Needs size and colour change. |
| `components/DisplayHeading.kt` (displayMedium plain + italic tertiary accent) | Only by ScreenHeader | **No.** Italic two-tone heading is the old Playfair editorial idea. Spec: headlineLarge Medium, one colour, no italic. Replace or delete. |
| `components/LightDarkPreviews.kt` (`@LightDarkPreviews`, `PreviewSurface`) | Yes, by the other new components' previews | **Yes** (tooling only). Keep. Preview names `Светла`/`Темна` are not user-facing. |
| `components/MacedonianDates.kt` (day/month names, `dayMonthMk`, `longDateMk`) | No | **Neutral.** Text helpers, not visual. `MacedonianShortDayNames` duplicates Home's private `shortDayNames`. Using `longDateMk()` anywhere would add new user-facing text (not allowed unless approved); reusing the short names for the existing week strip is fine. |
| `components/PillButtons.kt` (`PillShape`, `PillButton` 56dp primary, `PillButtonOutlined` 1dp outline-colour border, `PillChip` 36dp ink fill) | No | **Partly.** Shape right. Spec 2: primary 52dp, disabled = accentSoft; outline border should be **accent**, not `outline`; secondary (greenContainer + accent label) missing. Spec 6: chip 32dp greenContainer, selected = green fill (single) or darkOlive outline (multi); current "ink fill / surface + outline" is wrong. `PillButton` forces `fillMaxWidth`, which doesn't suit side-by-side sheet footers. |
| `components/PillSearchField.kt` (52dp pill BasicTextField on surfaceVariant, search icon) | No | **Mostly.** Spec 4: 48dp, greenContainer (= surfaceVariant after the theme change), **accent** search icon. Uses placeholder as contentDescription. Keep, adjust height/icon tint. Note it has no visible label, while today's Ingredients/Recipes fields use a floating label (`Пребарај` / `Пребарај рецепти`); using the label text as placeholder keeps the same string. |
| `components/ScreenHeader.kt` (eyebrow + action icons + DisplayHeading) | No | **No as is.** The eyebrow would need new text (e.g. a date) and the plain/accent split would change titles. Spec: headlineLarge title + circle buttons. Rework into a simple title + actions header that shows the existing title strings unchanged. |
| `components/SectionCard.kt` (shapes.large, 2dp shadow, outlineVariant hairline, 20dp padding) | No | **Partly.** 20dp corners and padding fit. Spec says **no elevation or shadows** and cards use the `card` colour (`#E3E5C4`), no hairline. Needs the shadow and border removed and colour changed. |
| `recipes/RecipeCover.kt` (Coil `AsyncImage` cover with scrim, `servingsLabel`, `recipeMetaLine`, `RecipeCoverTitle`) | No | **No, and it does not compile on its own terms:** it imports `coil.compose.*` but Coil is not a Gradle dependency (adding it is not allowed), and it calls `MojRucekTheme.colors`, which does not exist (`MojRucekTheme` is only a function). Also `recipeMetaLine`/`servingsLabel` would add new text (`1 порција` / `$n порции` on cards). Full-bleed photo with scrim is not the spec's circle-in-well card. Needs rework (no Coil) or deletion. See build note below. |
| `theme/MojRucekColors.kt` (extra colours for week strip, days, reminder, onImage; `LocalMojRucekColors`) | No (not provided in `MojRucekTheme`) | **Idea fits, values don't.** An extra-token holder is the right place for spec tokens with no M3 role (green, greenContainer, card, darkOlive, accentSoft, divider, textSecondary). The current lavender/grey values are not in the spec. Needs new fields and values and a `CompositionLocalProvider` plus a `MojRucekTheme.colors` accessor in Theme.kt. |
| `theme/Spacing.kt` (20dp edge, 16dp between cards, 20dp card padding, 64dp nav height, nav/FAB clearances) | Only by SectionCard | **Yes.** Matches spec "Shape and spacing" and nav bar height. Add 24dp `betweenSections` and 12dp `insideRow` from the spec. |
| `res/font/playfair_display*.ttf`, `licenses/PlayfairDisplay-OFL.txt` | No code references them | **No.** Spec drops Playfair and uses Inter. Inter Regular/Medium/SemiBold + its OFL licence must be added; the Playfair files can be removed. |
| `app/schemas/com.mojrucek.app.data.AppDatabase/1.json` | Room schema export (generated) | Not UI. Outside the allowed redesign paths; leave as is, don't commit it as part of the redesign. |

### Build note
`./gradlew compileDebugKotlin` (JBR 21.0.11) **fails today, before any redesign change**, with 15 errors, all in
`ui/recipes/RecipeCover.kt`: `Unresolved reference 'coil'` / `'AsyncImage'` / `'AsyncImagePainter'` (lines 23–68) and
`Unresolved reference 'colors'` on `MojRucekTheme.colors` (line 102). Every other untracked file compiles.
The first redesign step must rewrite RecipeCover without Coil (the "no photo" fallback only) or delete it. Adding Coil
is a Gradle dependency change, so it is not allowed without approval.

## 20. Things the redesign must not lose (summary checklist)
- All 9 routes and the 5-tab nav with its contentDescriptions `Почетна`, `Намирници`, `Рецепти`, `Планер`, `Пазарење`.
- Both one-shot navigations from Home (low stock → Ingredients filter; reminder → next week in Planner).
- Every long-press: Shopping `Во инвентар` and `За сега` segments (partial purchase), with the a11y label `Делумно купено`.
- Both swipe directions on Shopping cards, and the `Врати` snackbar undo.
- Ingredient −/+ steppers, ⋮ menu (`Уреди`, `Избриши`), row tap to edit, the four sort options, two toggle chips, category chips with counts.
- Recipes: 4 readiness chips + favourites toggle + 3 time chips, heart per card, FAB.
- RecipeDetail: 4 top actions (favourite, edit, copy, delete), servings stepper, add-missing button, go-to-shopping button, cooked button, both snackbars, locked state.
- RecipeEdit: every field, add/remove ingredient line, add/remove step, unit and category dropdowns, disabled save.
- Planner: week prev/next/today, household servings stepper, day card tap + pick button, copy-from-day, clear day, generate/regenerate, limit notice (with Google link variant), generation result with conflicts.
- Settings: servings stepper, reminder switch, weekly/daily segment, day circles (single vs multi), hour/minute steppers with wrap-around, catalog sync, save/share/import backup, four message dialogs.
- Account: every AccountCard state, email dialog modes, password reset, switch-account flow (Explain → MakingSnapshot → Confirm → Switching), sign-out confirm, delete-account flow (Confirm → Working → Problem), guest expired dialog, legacy data prompt, sync card statuses, sync conflict dialog.
- Welcome three buttons + busy state; ConfigErrorScreen stays without buttons.
- Every busy/disabled state listed above (spinners and `enabled = !busy` flags).
