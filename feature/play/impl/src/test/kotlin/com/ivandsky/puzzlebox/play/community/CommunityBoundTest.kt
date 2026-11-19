package com.ivandsky.puzzlebox.play.community

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** New today's page in [CommunityBound]: what a row, its menu, the notice and the sign-in prompt do. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class CommunityBoundTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()

    @After
    fun stopGraph() = stopKoin()

    private fun show(fixture: CommunityFixture) {
        stopKoin()
        startKoin { modules(module { viewModel { fixture.viewModel() } }) }
        rule.setContent {
            PuzzleBoxTheme {
                CommunityPageHost(onOpen = { opened += it })
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the section lists the puzzles and a row opens by its code`() {
        val fixture = CommunityFixture().also { runBlocking { it.publish("AAA111", NOW - HOUR, difficulty = Level.Hard) } }
        show(fixture)

        rule.onNodeWithTag("community.row.AAA111").performClick()

        assertEquals(listOf("AAA111"), opened)
    }

    @Test
    fun `a labelled row shows its level's mark beside the word, an unlabelled row shows neither`() {
        show(
            CommunityFixture().also {
                runBlocking {
                    it.publish("AAA111", NOW - HOUR, difficulty = Level.Hard)
                    it.publish("BBB222", NOW - 2 * HOUR)
                }
            },
        )

        val meta = hasAnyAncestor(hasTestTag("community.row.AAA111.meta"))
        rule.onNode(hasTestTag("difficulty.hard") and meta, useUnmergedTree = true).assertIsDisplayed()
        rule.onNode(hasText("Hard") and meta, useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodes(hasTestTag("difficulty.hard"), useUnmergedTree = true).assertCountEquals(1)
        rule.onNode(hasAnyAncestor(hasTestTag("community.row.BBB222")) and hasText("Hard"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Report in a row's menu drops the row and shows the notice`() {
        val fixture = CommunityFixture().also { runBlocking { it.publish("AAA111", NOW - HOUR) } }
        show(fixture)

        rule.onNodeWithTag("community.row.AAA111.more").performClick()
        rule.onNodeWithTag("community.row.AAA111.report").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("community.row.AAA111").assertDoesNotExist()
        rule.onNodeWithText("Reported. It's hidden on this device.").assertIsDisplayed()
    }

    @Test
    fun `Report while signed out asks to sign in, and Not now closes the prompt`() {
        val fixture = CommunityFixture(signedIn = false).also { runBlocking { it.publish("AAA111", NOW - HOUR) } }
        show(fixture)

        rule.onNodeWithTag("community.row.AAA111.more").performClick()
        rule.onNodeWithTag("community.row.AAA111.report").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Sign in to report").assertIsDisplayed()
        rule.onNodeWithTag("community.signin.dismiss").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("community.signin").assertDoesNotExist()
        rule.onNodeWithTag("community.row.AAA111").assertIsDisplayed()
    }

    @Test
    fun `offline shows Retry, which loads the list once the connection is back`() {
        val fixture = CommunityFixture().also {
            runBlocking { it.publish("AAA111", NOW - HOUR) }
            it.shared.online = false
        }
        show(fixture)
        rule.onNodeWithTag("community.offline").assertIsDisplayed()

        fixture.shared.online = true
        rule.onNodeWithText("Retry").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("community.row.AAA111").assertIsDisplayed()
    }
}
