package com.ivandsky.puzzlebox.play.community

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
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

/** [CommunityHost] around a stand-in Custom list: what the flag adds, what a row, its menu and the sign-in prompt do. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class CommunityHostTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private val handed = mutableStateOf<Boolean?>(null)

    @After
    fun stopGraph() = stopKoin()

    private fun show(fixture: CommunityFixture) {
        stopKoin()
        startKoin { modules(module { viewModel { fixture.viewModel() } }) }
        rule.setContent {
            PuzzleBoxTheme {
                CommunityHost(onOpen = { opened += it }) { community ->
                    handed.value = community != null
                    LazyColumn { community?.invoke(this) }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `with the flag off the list is handed no section`() {
        show(CommunityFixture(flagOn = false))

        assertEquals(false, handed.value)
        rule.onNodeWithTag("community.head").assertDoesNotExist()
    }

    @Test
    fun `with the flag on the section lists the puzzles and a row opens by its code`() {
        val fixture = CommunityFixture().also { runBlocking { it.publish("AAA111", NOW - HOUR, difficulty = Level.Hard) } }
        show(fixture)

        rule.onNodeWithText("New today").assertIsDisplayed()
        rule.onNodeWithTag("community.row.AAA111").performClick()

        assertEquals(listOf("AAA111"), opened)
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
