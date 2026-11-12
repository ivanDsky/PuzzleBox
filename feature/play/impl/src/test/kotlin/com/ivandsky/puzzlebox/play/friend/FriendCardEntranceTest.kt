package com.ivandsky.puzzlebox.play.friend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card's entrance plays once per code: the first show animates; recomposing, leaving for
 * the board and coming back, or a restored instance state shows the card settled; another
 * code animates again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FriendCardEntranceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val first = requireNotNull(ShareCode.parse("K7Q2MX"))
    private val second = requireNotNull(ShareCode.parse("B4N8TW"))

    @Test
    fun `the first show of a code animates`() {
        var entrance: CardEntrance? = null
        composeRule.setContent { entrance = rememberCardEntrance(first) }
        composeRule.waitForIdle()

        assertTrue(requireNotNull(entrance).animates)
    }

    @Test
    fun `recomposing keeps the entrance it started with`() {
        var tick by mutableIntStateOf(0)
        val seen = mutableListOf<CardEntrance>()
        composeRule.setContent {
            tick.let { seen += rememberCardEntrance(first) }
        }
        composeRule.waitForIdle()
        tick++
        composeRule.waitForIdle()

        assertSame(seen.first(), seen.last())
    }

    @Test
    fun `coming back from the board does not run the entrance again`() {
        var onCard by mutableStateOf(true)
        var entrance: CardEntrance? = null
        composeRule.setContent {
            val holder = rememberSaveableStateHolder()
            if (onCard) holder.SaveableStateProvider("friend") { entrance = rememberCardEntrance(first) }
        }
        composeRule.waitForIdle()
        onCard = false
        composeRule.waitForIdle()
        onCard = true
        composeRule.waitForIdle()

        assertFalse(requireNotNull(entrance).animates)
    }

    @Test
    fun `a restored card does not run the entrance again`() {
        val restoration = StateRestorationTester(composeRule)
        var entrance: CardEntrance? = null
        restoration.setContent { entrance = rememberCardEntrance(first) }
        composeRule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertFalse(requireNotNull(entrance).animates)
    }

    @Test
    fun `another code animates again`() {
        var code by mutableStateOf(first)
        var entrance: CardEntrance? = null
        composeRule.setContent { entrance = rememberCardEntrance(code) }
        composeRule.waitForIdle()
        code = second
        composeRule.waitForIdle()

        assertTrue(requireNotNull(entrance).animates)
    }
}
