package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/** RulesSheet off, with a rules lookup that would answer: How to play opens the tutorial, never the sheet. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class RulesSheetFlagOffRouteTest(custom: Boolean) : GameTutorialFixture(custom, playFeel = true, rulesSheet = false) {

    @Test
    fun `how to play opens the tutorial and no rules sheet`() {
        fakes[alpha].teach(2)
        start(FakeUserPreferencesRepository(seen = setOf(alpha)))

        click("play.how_to_play")

        composeRule.onNodeWithTag("tutorial.host").assertExists()
        assertTrue(composeRule.onAllNodesWithTag("rules.sheet").fetchSemanticsNodes().isEmpty())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun entries(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
