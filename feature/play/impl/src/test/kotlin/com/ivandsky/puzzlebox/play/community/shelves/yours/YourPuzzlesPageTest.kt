package com.ivandsky.puzzlebox.play.community.shelves.yours

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Your puzzles page: its title and back, the slot's Yours list inside it, and the words when there is no slot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YourPuzzlesPageTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `it is titled Your puzzles and hosts the slot's list`() {
        rule.setContent {
            PuzzleBoxTheme {
                YourPuzzlesPage(yours = { state -> LazyColumn(state = state) { items(listOf("Draft 1", "Mine 2")) { Text(it, Modifier.testTag("row")) } } }, onBack = {})
            }
        }
        rule.onNodeWithText("Draft 1").assertExists()
        rule.onNodeWithText("Mine 2").assertExists()
        rule.onNodeWithText("Your puzzles").assertExists()
        rule.onNodeWithTag("yours.empty").assertDoesNotExist()
    }

    @Test
    fun `back asks to close`() {
        var closed = 0
        rule.setContent { PuzzleBoxTheme { YourPuzzlesPage(yours = null, onBack = { closed++ }) } }
        rule.onNodeWithTag("yours.back").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun `without a slot it says there is nothing here`() {
        rule.setContent { PuzzleBoxTheme { YourPuzzlesPage(yours = null, onBack = {}) } }
        rule.onNodeWithText("No puzzles of yours here").assertExists()
        rule.onNodeWithText("Puzzles you make and publish show up here.").assertExists()
    }

    @Test
    fun `the slot's list stops above the bottom padding`() {
        rule.setContent {
            PuzzleBoxTheme {
                YourPuzzlesPage(yours = { Box(Modifier.fillMaxSize().testTag("slot")) }, onBack = {}, bottomPadding = 80.dp)
            }
        }
        val page = rule.onNodeWithTag("yours.page").getUnclippedBoundsInRoot().bottom
        val slot = rule.onNodeWithTag("slot").getUnclippedBoundsInRoot().bottom
        assertTrue("slot ends at $slot, page at $page", slot <= page - 80.dp)
    }
}
