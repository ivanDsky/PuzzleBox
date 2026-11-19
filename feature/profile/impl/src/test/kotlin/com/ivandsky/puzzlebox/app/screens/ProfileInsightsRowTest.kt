package com.ivandsky.puzzlebox.app.screens

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Profile's Insights row: drawn only when the shell hands Profile a way to open Insights, and one tap on it opens Insights. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ProfileInsightsRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var opened = 0

    private fun show(onOpenInsights: (() -> Unit)?) = composeRule.setContent {
        PuzzleBoxTheme {
            ProfileContent(
                state = ProfileUiState(account = ProfileUiState.Account.SignedOut()),
                onSignIn = {},
                onSignOut = {},
                onBack = {},
                onOpenSettings = {},
                nowMs = 0L,
                onSyncNow = {},
                onDeleteCloudData = {},
                onDeleteSavedBoards = {},
                onOpenInsights = onOpenInsights,
            )
        }
    }

    @Test
    fun `with Insights on the row is drawn and opens Insights once`() {
        show(onOpenInsights = { opened++ })

        composeRule.onNodeWithTag("profile.insights").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `with Insights off no row is drawn`() {
        show(onOpenInsights = null)

        composeRule.onNodeWithTag("profile.insights").assertDoesNotExist()
    }

    @Test
    fun `the row is at least 48 dp tall`() {
        show(onOpenInsights = {})

        val height = composeRule.onNodeWithTag("profile.insights").performScrollTo().getUnclippedBoundsInRoot().height
        assertTrue("row is $height tall", height >= 48.dp)
    }
}
