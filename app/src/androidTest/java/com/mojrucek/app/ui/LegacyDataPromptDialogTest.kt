package com.mojrucek.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mojrucek.app.ui.auth.LegacyDataPromptDialog
import com.mojrucek.app.ui.theme.MojRucekTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The question about the data from before accounts, as the user sees it. */
@RunWith(AndroidJUnit4::class)
class LegacyDataPromptDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private var kept = 0
    private var later = 0
    private var saved = 0

    private fun show(message: String? = null) = compose.setContent {
        MojRucekTheme {
            LegacyDataPromptDialog(message = message, onKeep = { kept++ }, onLater = { later++ }, onSaveCopy = { saved++ })
        }
    }

    @Test fun showsTheQuestion_withThreeChoices_andReassuresThatNothingIsDeleted() {
        show()

        compose.onNodeWithText("Податоци од пред сметките").assertIsDisplayed()
        compose.onNodeWithText("Задржи во оваа сметка").assertIsDisplayed()
        compose.onNodeWithText("Зачувај копија прво").assertIsDisplayed()
        compose.onNodeWithText("Подоцна").assertIsDisplayed()
        compose.onNodeWithText("Ништо не се брише", substring = true).assertIsDisplayed()
    }

    @Test fun eachChoice_doesExactlyItsOwnThing() {
        show()

        compose.onNodeWithText("Зачувај копија прво").performClick()
        assertEquals(listOf(0, 0, 1), listOf(kept, later, saved))
        compose.onNodeWithText("Подоцна").performClick()
        assertEquals(listOf(0, 1, 1), listOf(kept, later, saved))
        compose.onNodeWithText("Задржи во оваа сметка").performClick()
        assertEquals(listOf(1, 1, 1), listOf(kept, later, saved))
    }

    @Test fun theResultOfSavingACopy_isShownInsideTheQuestion() {
        show(message = "Резервната копија е успешно зачувана.")

        compose.onNodeWithText("Резервната копија е успешно зачувана.").assertIsDisplayed()
        compose.onNodeWithText("Задржи во оваа сметка").assertIsDisplayed()   // the question is still there to answer
    }
}
