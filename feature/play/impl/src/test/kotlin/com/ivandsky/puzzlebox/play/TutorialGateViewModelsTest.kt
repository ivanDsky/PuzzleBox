package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tutorial gate on both board entries that carry it: the daily's [GameViewModel] and a
 * friend's puzzle's [CustomGameViewModel] hold the same [TutorialGateHolder], so the daily's fixtures run over each.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class TutorialGateViewModelsTest(private val custom: Boolean) : GameTutorialFixture(custom) {

    @Test
    fun `unseen lessons open before the board and finishing them marks the game seen`() {
        fakes[alpha].teach(2)
        val prefs = FakeUserPreferencesRepository()
        start(prefs)

        composeRule.onNodeWithTag("tutorial.host").assertExists()
        composeRule.onNodeWithContentDescription("Step 1 of 2").assertExists()
        winLesson()
        winLesson()
        assertTrue(prefs.seen.isEmpty())

        click("tutorial.finish")
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(setOf(alpha), prefs.seen)
    }

    @Test
    fun `a seen game shows its board at once and the bar offers how to play`() {
        fakes[alpha].teach(2)
        start(FakeUserPreferencesRepository(seen = setOf(alpha)))

        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        composeRule.onNodeWithTag("play.how_to_play").assertExists()
    }

    @Test
    fun `a game without lessons shows its board and offers no how to play`() {
        start(FakeUserPreferencesRepository(), game = beta)

        composeRule.onNodeWithText("beta board 0").assertExists()
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithTag("play.how_to_play").assertDoesNotExist()
    }

    @Test
    fun `skip marks the game seen and shows its board`() {
        fakes[alpha].teach(3)
        val prefs = FakeUserPreferencesRepository()
        start(prefs)

        click("tutorial.skip")

        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(setOf(alpha), prefs.seen)
    }

    @Test
    fun `back from the lessons leaves the game unseen`() {
        fakes[alpha].teach(2)
        val prefs = FakeUserPreferencesRepository()
        start(prefs)

        click("game.up")

        assertEquals(1, backs)
        assertTrue(prefs.seen.isEmpty())
    }

    @Test
    fun `the clock window is null behind the lessons and they write no record`() {
        fakes[alpha].teach(1)
        start(FakeUserPreferencesRepository())
        val play = live().controller as FakeController

        composeRule.onNodeWithTag("tutorial.host").assertExists()
        assertNull("lesson time never counts", play.clockWindow)
        winLesson()
        assertTrue(records().isEmpty())
        assertEquals(0, solved)
        assertEquals(0, play.moves)

        click("tutorial.finish")
        assertNotNull("the board's clock runs once it shows", play.clockWindow)
    }

    @Test
    fun `a recomposed entry keeps the open lesson step`() {
        fakes[alpha].teach(3)
        start(FakeUserPreferencesRepository())
        winLesson()
        composeRule.onNodeWithContentDescription("Step 2 of 3").assertExists()

        composeRule.runOnIdle { entry.intValue++ }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Step 2 of 3").assertExists()
    }

    @Test
    fun `a resumed saved play follows the same rule`() {
        fakes[alpha].teach(2)
        val slot = if (custom) PlaySlot.Custom(alpha, "K7Q2MX") else PlaySlot.Daily(alpha, day)
        runBlocking {
            saved.save(slot, SessionBlob.encode(alpha, "fake|2|false|$day|7|5000".encodeToByteArray()), elapsedMs = 5_000L)
        }
        start(FakeUserPreferencesRepository())

        composeRule.onNodeWithTag("tutorial.host").assertExists()
        click("tutorial.skip")
        composeRule.onNodeWithText("alpha board 2").assertExists()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun entries(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
