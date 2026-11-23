package com.mojrucek.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.data.entity.IngredientCategory
import com.mojrucek.app.data.entity.Recipe
import com.mojrucek.app.data.entity.RecipeIngredient
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.repository.AccountSwitchRepository
import com.mojrucek.app.repository.BackupRepository
import com.mojrucek.app.repository.CurrentOwner
import com.mojrucek.app.repository.ImportResult
import com.mojrucek.app.repository.InMemoryOwnershipPrefs
import com.mojrucek.app.repository.IngredientRepository
import com.mojrucek.app.repository.OwnerKeys
import com.mojrucek.app.repository.OwnershipClaimer
import com.mojrucek.app.repository.OwnershipCoordinator
import com.mojrucek.app.repository.LegacyState
import com.mojrucek.app.repository.PlannerRepository
import com.mojrucek.app.repository.RecipeRepository
import com.mojrucek.app.repository.SettingsRepository
import com.mojrucek.app.repository.ShoppingListRepository
import com.mojrucek.app.repository.SnapshotResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Two (or three) accounts on ONE database: each must only ever see and change its own data, while the
 * shared server recipes are visible to everyone. Real in-memory Room database; never the app's real data.
 */
@RunWith(AndroidJUnit4::class)
class OwnershipIsolationInstrumentedTest {

    private val A = "uid-account-A"
    private val B = "uid-account-B"
    private val week = LocalDate.of(2026, 11, 23)

    private val who = MutableStateFlow(A)
    private val owner = CurrentOwner.following(who)

    private lateinit var db: AppDatabase
    private lateinit var tempFiles: File
    private lateinit var recipes: RecipeRepository
    private lateinit var ingredients: IngredientRepository
    private lateinit var shopping: ShoppingListRepository
    private lateinit var planner: PlannerRepository
    private lateinit var backup: BackupRepository
    private lateinit var switcher: AccountSwitchRepository

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        tempFiles = File(target.cacheDir, "isolation-test-${System.nanoTime()}").apply { mkdirs() }
        val context: Context = object : ContextWrapper(target) {
            override fun getFilesDir(): File = tempFiles
        }
        db = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java).build()
        recipes = RecipeRepository(db.recipeDao(), db.favoriteDao(), owner)
        ingredients = IngredientRepository(db.ingredientDao(), owner)
        shopping = ShoppingListRepository(db.shoppingListDao(), ingredients, owner)
        planner = PlannerRepository(db.plannerDao(), recipes, ingredients, shopping, owner)
        backup = BackupRepository(ingredients, recipes, shopping, db.plannerDao(), SettingsRepository(db.appSettingsDao()), db, owner)
        switcher = AccountSwitchRepository(context, db, backup, owner)
    }

    @After
    fun tearDown() {
        db.close()
        tempFiles.deleteRecursively()
    }

    private fun line(name: String) = RecipeIngredient(
        recipeId = 0, name = name, normalizedName = name.lowercase(), quantity = 1.0, unit = MeasurementUnit.PIECE
    )

    /** A shared server recipe (no owner), as the catalog sync stores it. */
    private suspend fun serverRecipe(name: String, serverId: Long): Long =
        recipes.upsert(Recipe(name = name, isUserCreated = false, serverId = serverId), listOf(line("сол")))

    /** Gives the CURRENT account a pantry item, a list item, an own recipe and a plan with a meal. */
    private suspend fun fill(tag: String) {
        ingredients.insert("Ингредиент $tag", IngredientCategory.DRUGO, 2.0, MeasurementUnit.PIECE, null, null, null)
        shopping.addOrCombine("Листа $tag", 1.0, MeasurementUnit.PIECE, IngredientCategory.DRUGO)
        val own = recipes.upsert(Recipe(name = "Рецепт $tag"), listOf(line("x")))
        val plan = planner.getOrCreatePlan(week, defaultServings = 2)
        planner.setMeal(plan.weeklyPlan.id, dayOfWeek = 1, recipeId = own)
    }

    private suspend fun names() = listOf(
        ingredients.getAllOnce().map { it.name },
        shopping.getAllOnce().map { it.name },
        recipes.getAllWithIngredientsOnce().map { it.recipe.name }.sorted(),
        planner.getPlanForWeek(week)?.meals?.size ?: -1
    )

    private fun asAccount(key: String) { who.value = key }

    @Test
    fun eachAccountSeesOnlyItsOwnData_andBothSeeTheSharedServerRecipes() = runBlocking {
        asAccount(A); serverRecipe("Сервер", 77); fill("A")
        asAccount(B); fill("B")

        asAccount(A)
        assertEquals(listOf(listOf("Ингредиент A"), listOf("Листа A"), listOf("Рецепт A", "Сервер"), 1), names())
        asAccount(B)
        assertEquals(listOf(listOf("Ингредиент B"), listOf("Листа B"), listOf("Рецепт B", "Сервер"), 1), names())
    }

    @Test
    fun theWatchedLists_followTheAccountThatIsSignedIn() = runBlocking {
        asAccount(A); fill("A")
        asAccount(B); fill("B")

        asAccount(A)
        assertEquals(listOf("Ингредиент A"), ingredients.observeAll().first().map { it.name })
        assertEquals(listOf("Листа A"), shopping.observeAll().first().map { it.name })
        assertEquals(listOf("Рецепт A"), recipes.observeAllWithIngredients().first().map { it.recipe.name })
        asAccount(B)
        assertEquals(listOf("Ингредиент B"), ingredients.observeAll().first().map { it.name })
        assertEquals(1, planner.observePlanForWeek(week).first()?.meals?.size)
    }

    @Test
    fun nobodySignedIn_seesNothing() = runBlocking {
        asAccount(A); fill("A"); serverRecipe("Сервер", 77)
        asAccount(OwnerKeys.SIGNED_OUT)

        assertEquals(listOf(emptyList<String>(), emptyList<String>(), listOf("Сервер"), -1), names())
        assertTrue(ingredients.observeAll().first().isEmpty())
    }

    @Test
    fun eachAccountHasItsOwnPlanForTheSameWeek() = runBlocking {
        asAccount(A); val planA = planner.getOrCreatePlan(week, 2).weeklyPlan
        asAccount(B); val planB = planner.getOrCreatePlan(week, 5).weeklyPlan

        assertNotEquals(planA.id, planB.id)
        assertEquals(A, planA.ownerUid)
        assertEquals(B, planB.ownerUid)
        assertEquals(5, planner.getPlanForWeek(week)!!.weeklyPlan.householdServings)
        asAccount(A)
        assertEquals(2, planner.getPlanForWeek(week)!!.weeklyPlan.householdServings)
    }

    @Test
    fun everyNewRowIsStampedWithTheAccount_andServerRecipesStayShared() = runBlocking {
        asAccount(A); fill("A"); val server = serverRecipe("Сервер", 77)

        assertTrue(ingredients.getAllOnce().all { it.ownerUid == A })
        assertTrue(shopping.getAllOnce().all { it.ownerUid == A })
        val all = recipes.getAllWithIngredientsOnce().associateBy { it.recipe.name }
        assertEquals(A, all.getValue("Рецепт A").recipe.ownerUid)
        assertEquals("a server recipe belongs to nobody", OwnerKeys.UNOWNED, all.getValue("Сервер").recipe.ownerUid)
        assertEquals(server, all.getValue("Сервер").recipe.id)
    }

    @Test
    fun aDuplicatedRecipe_isTheDuplicatingAccountsOwn_withItsOwnIdentity() = runBlocking {
        asAccount(A); val server = serverRecipe("Сервер", 77)
        asAccount(B)
        val copyId = recipes.duplicate(recipes.getWithIngredients(server)!!)

        val copy = recipes.getWithIngredients(copyId)!!.recipe
        assertEquals(B, copy.ownerUid)
        assertEquals(null, copy.serverId)
        assertNotEquals(recipes.getWithIngredients(server)!!.recipe.uuid, copy.uuid)
        asAccount(A)
        assertEquals("the other account does not see B's copy", null, recipes.getWithIngredients(copyId))
    }

    @Test
    fun clearingOrRestoringOneAccount_neverTouchesAnother() = runBlocking {
        asAccount(A); serverRecipe("Сервер", 77); fill("A")
        asAccount(B); fill("B")
        val bBefore = names()

        asAccount(A)
        val file = backup.exportToJson()
        assertFalse("a backup of A holds nothing of B", file.contains("Ингредиент B") || file.contains("Рецепт B") || file.contains("Листа B"))
        assertFalse("and no account id", file.contains(A) || file.contains(B))

        // A changes things, then restores its backup. B has plans/meals with the very same database ids.
        shopping.clearAll(); ingredients.clearAll()
        ingredients.insert("Нешто друго", IngredientCategory.DRUGO, 1.0, MeasurementUnit.PIECE, null, null, null)
        val result = backup.import(file)

        assertTrue("restore should succeed: $result", result is ImportResult.Success)
        assertEquals(listOf(listOf("Ингредиент A"), listOf("Листа A"), listOf("Рецепт A", "Сервер"), 1), names())
        asAccount(B)
        assertEquals("B is exactly as before", bBefore, names())
    }

    @Test
    fun restoringABackup_keepsMealsOnTheRightRecipes_whenAnotherAccountOccupiesTheOldIds() = runBlocking {
        asAccount(A); fill("A")
        asAccount(B); fill("B")
        asAccount(A)
        val file = backup.exportToJson()
        backup.import(file); backup.import(file)   // twice: ids keep moving, nothing must break or mix

        val plan = planner.getPlanForWeek(week)!!
        val recipeName = recipes.getWithIngredients(plan.meals.single().recipeId!!)!!.recipe.name
        assertEquals("Рецепт A", recipeName)
        asAccount(B)
        assertEquals("Рецепт B", recipes.getWithIngredients(planner.getPlanForWeek(week)!!.meals.single().recipeId!!)!!.recipe.name)
    }

    @Test
    fun leavingAGuestBehind_clearsOnlyTheGuestsRows_notTheAccountThatSignedInAfterwards() = runBlocking {
        val guest = "uid-guest"
        asAccount(guest); serverRecipe("Сервер", 77); fill("гостин")
        asAccount(B); fill("B")   // the existing account that already has its own data

        asAccount(guest)
        val snapshot = (switcher.createVerifiedSnapshot() as SnapshotResult.Ok).snapshot
        assertEquals(guest, snapshot.ownerKey)
        asAccount(B)               // the guest signs in to the existing account B

        assertTrue("the guest's data still matches its snapshot", switcher.matchesSnapshot(snapshot))
        assertTrue("clearing succeeds", switcher.clearLocalUserData(snapshot))

        assertEquals("B untouched", listOf(listOf("Ингредиент B"), listOf("Листа B"), listOf("Рецепт B", "Сервер"), 1), names())
        asAccount(guest)
        assertEquals("the guest's rows are gone, the shared recipe stays",
            listOf(emptyList<String>(), emptyList<String>(), listOf("Сервер"), -1), names())
    }

    @Test
    fun favouritesBelongToTheAccount_notToTheRecipe() = runBlocking {
        asAccount(A); val server = serverRecipe("Сервер", 77); val other = serverRecipe("Друг сервер", 78)
        val ownA = recipes.upsert(Recipe(name = "Рецепт A"), listOf(line("x")))
        recipes.setFavorite(server, true); recipes.setFavorite(ownA, true)

        asAccount(B)
        assertEquals("B sees none of A's favourites", emptyList<String>(), recipes.getAllWithIngredientsOnce().filter { it.recipe.isFavorite }.map { it.recipe.name })
        recipes.setFavorite(other, true)
        assertEquals(listOf("Друг сервер"), recipes.getAllWithIngredientsOnce().filter { it.recipe.isFavorite }.map { it.recipe.name })

        asAccount(A)
        assertEquals(listOf("Рецепт A", "Сервер"), recipes.getAllWithIngredientsOnce().filter { it.recipe.isFavorite }.map { it.recipe.name }.sorted())
        assertTrue(recipes.getWithIngredients(server)!!.recipe.isFavorite)
        assertFalse(recipes.getWithIngredients(other)!!.recipe.isFavorite)
        assertEquals(listOf("Рецепт A", "Сервер"), recipes.observeAllWithIngredients().first().filter { it.recipe.isFavorite }.map { it.recipe.name }.sorted())
        assertTrue(recipes.observeWithIngredients(server).first()!!.recipe.isFavorite)

        recipes.setFavorite(server, false)
        assertFalse("un-favouriting works", recipes.getWithIngredients(server)!!.recipe.isFavorite)
        asAccount(B)
        assertEquals("and does not affect B", listOf("Друг сервер"), recipes.getAllWithIngredientsOnce().filter { it.recipe.isFavorite }.map { it.recipe.name })
    }

    @Test
    fun deletingAnOwnRecipe_removesItsFavouriteMark() = runBlocking {
        asAccount(A); val id = recipes.upsert(Recipe(name = "Мој"), listOf(line("x")))
        recipes.setFavorite(id, true)
        assertEquals(1, db.favoriteDao().getRefs(A).size)

        recipes.delete(recipes.getWithIngredients(id)!!.recipe)

        assertEquals(0, db.favoriteDao().getRefs(A).size)
    }

    @Test
    fun backupAndRestore_carryOnlyTheAccountsOwnFavourites() = runBlocking {
        asAccount(A); val server = serverRecipe("Сервер", 77)
        val own = recipes.upsert(Recipe(name = "Рецепт A"), listOf(line("x")))
        recipes.setFavorite(server, true); recipes.setFavorite(own, true)
        asAccount(B); recipes.setFavorite(server, true)
        asAccount(A)
        val file = backup.exportToJson()
        recipes.setFavorite(server, false)               // A changes its mind, then restores
        recipes.setFavorite(recipes.getAllWithIngredientsOnce().first { it.recipe.name == "Рецепт A" }.recipe.id, false)

        assertTrue(backup.import(file) is ImportResult.Success)

        assertEquals("A's favourites are back", listOf("Рецепт A", "Сервер"), recipes.getAllWithIngredientsOnce().filter { it.recipe.isFavorite }.map { it.recipe.name }.sorted())
        asAccount(B)
        assertEquals("B's favourite is untouched", listOf("Сервер"), recipes.getAllWithIngredientsOnce().filter { it.recipe.isFavorite }.map { it.recipe.name })
    }

    @Test
    fun leavingAGuestBehind_removesTheGuestsFavourites_notTheOthers() = runBlocking {
        val guest = "uid-guest"
        asAccount(guest); val server = serverRecipe("Сервер", 77); recipes.setFavorite(server, true)
        asAccount(B); recipes.setFavorite(server, true)
        asAccount(guest)
        val snapshot = (switcher.createVerifiedSnapshot() as SnapshotResult.Ok).snapshot
        asAccount(B)

        assertTrue(switcher.clearLocalUserData(snapshot))

        assertEquals(emptyList<String>(), db.favoriteDao().getRefs(guest))
        assertEquals(listOf("s:77"), db.favoriteDao().getRefs(B))
    }

    /** Data as it exists right after the v3 -> v4 upgrade: no owner (1 pantry item, 1 list item, 1 plan, 1 own recipe, 1 favourite). */
    private suspend fun fillLikeAfterTheUpgrade() {
        asAccount(OwnerKeys.UNOWNED); fill("старо"); serverRecipe("Сервер", 77)
        db.openHelper.writableDatabase.execSQL("INSERT INTO recipe_favorites (ownerUid, recipeRef, updatedAt, deletedAt, dirty) VALUES ('', 's:77', 1, NULL, 1)")
        assertEquals(5, db.ownershipDao().countUnowned())
    }

    @Test
    fun claiming_handsEverythingWithoutAnOwnerToTheAccount_andNothingElse() = runBlocking {
        asAccount(B); fill("B")
        fillLikeAfterTheUpgrade()

        val claimer = OwnershipClaimer(db)
        assertEquals("nobody signed in is not an account: nothing moves", 0, claimer.claimFor(OwnerKeys.SIGNED_OUT))
        assertEquals(0, claimer.claimFor(OwnerKeys.UNOWNED))
        assertEquals(5, claimer.claimFor(A))

        asAccount(A)
        assertEquals(listOf(listOf("Ингредиент старо"), listOf("Листа старо"), listOf("Рецепт старо", "Сервер"), 1), names())
        assertEquals("the old favourite now belongs to A and shows on the recipe", listOf("Сервер"), recipes.getAllWithIngredientsOnce().filter { it.recipe.isFavorite }.map { it.recipe.name })
        assertEquals("nothing is left without an owner", 0, db.ownershipDao().countUnowned())
        assertEquals("the shared server recipe never gets an owner", OwnerKeys.UNOWNED,
            recipes.getAllWithIngredientsOnce().first { it.recipe.serverId == 77L }.recipe.ownerUid)
        asAccount(B)
        assertEquals("B's data is untouched", listOf(listOf("Ингредиент B"), listOf("Листа B")), names().take(2))
    }

    @Test
    fun claiming_aLeftoverDuplicateWeek_doesNotBreakTheRest() = runBlocking {
        asAccount(OwnerKeys.UNOWNED); fill("старо")             // unowned data with a plan for this week
        asAccount(A); planner.getOrCreatePlan(week, 3)          // A already has a plan for the same week

        val claimed = OwnershipClaimer(db).claimFor(A)

        assertTrue("the rest is still handed over", claimed >= 3)
        asAccount(A)
        assertEquals(listOf("Ингредиент старо"), ingredients.getAllOnce().map { it.name })
        assertEquals("A keeps exactly its own plan for the week", 3, planner.getPlanForWeek(week)!!.weeklyPlan.householdServings)
    }

    @Test
    fun signedInAtTheFirstLook_getsTheDataWithoutAQuestion() = runBlocking {
        fillLikeAfterTheUpgrade()
        val prefs = InMemoryOwnershipPrefs()
        val coordinator = OwnershipCoordinator(OwnershipClaimer(db), prefs)

        coordinator.onOwnerChanged(A)

        assertFalse(coordinator.promptNeeded.value)
        assertEquals(LegacyState.DONE, prefs.legacyState)
        asAccount(A)
        assertEquals(listOf("Ингредиент старо"), ingredients.getAllOnce().map { it.name })
    }

    @Test
    fun nobodySignedInAtTheFirstLook_theDataStaysHidden_untilTheAccountAnswersTheQuestion() = runBlocking {
        asAccount(B); fill("B")
        fillLikeAfterTheUpgrade()
        val prefs = InMemoryOwnershipPrefs()
        val coordinator = OwnershipCoordinator(OwnershipClaimer(db), prefs)

        coordinator.onOwnerChanged(OwnerKeys.SIGNED_OUT)
        assertEquals(LegacyState.PENDING, prefs.legacyState)

        coordinator.onOwnerChanged(A)                           // A signs in: asked, nothing moved yet
        assertTrue(coordinator.promptNeeded.value)
        asAccount(A)
        assertEquals("the old data is hidden from A until A says keep", emptyList<String>(), ingredients.getAllOnce().map { it.name })
        assertEquals(5, db.ownershipDao().countUnowned())

        coordinator.askLater()                                  // "later": still hidden, still unowned
        assertEquals(5, db.ownershipDao().countUnowned())
        assertEquals(emptyList<String>(), ingredients.getAllOnce().map { it.name })

        coordinator.onOwnerChanged(A)
        coordinator.keep(A)                                     // "keep"
        assertFalse(coordinator.promptNeeded.value)
        assertEquals(LegacyState.DONE, prefs.legacyState)
        assertEquals(0, db.ownershipDao().countUnowned())
        assertEquals(listOf("Ингредиент старо"), ingredients.getAllOnce().map { it.name })
        asAccount(B)
        assertEquals("B is untouched", listOf("Ингредиент B"), ingredients.getAllOnce().map { it.name })
    }

    @Test
    fun savingACopyFirst_savesTheOldUnownedData_andChangesNothing() = runBlocking {
        asAccount(B); fill("B")
        fillLikeAfterTheUpgrade()
        asAccount(A)

        val file = backup.exportToJson(OwnerKeys.UNOWNED)       // what "Зачувај копија прво" writes

        assertTrue(file.contains("Ингредиент старо") && file.contains("Листа старо") && file.contains("Рецепт старо"))
        assertFalse("and nothing of another account", file.contains("Ингредиент B") || file.contains("Рецепт B"))
        assertEquals("exporting moves nothing", 5, db.ownershipDao().countUnowned())
    }
}
