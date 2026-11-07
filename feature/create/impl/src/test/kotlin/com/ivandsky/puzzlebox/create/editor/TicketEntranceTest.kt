package com.ivandsky.puzzlebox.create.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.ivandsky.puzzlebox.designsystem.ContainerTransform
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The ticket's stamp plays once per code: a code published here starts unstamped and lands
 * after the card grows in; a reopened code, a restored instance and the same code recomposed
 * show it landed; another fresh code stamps again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TicketEntranceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val first = requireNotNull(ShareCode.parse("K7Q2MX"))
    private val second = requireNotNull(ShareCode.parse("B4N8TW"))

    @Test
    fun `a reopened code shows the ticket landed from the first frame`() {
        val seen = mutableListOf<Boolean>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { seen += rememberTicketLanded(first, stamps = false) }
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(seen.isNotEmpty() && seen.all { it })
    }

    @Test
    fun `a code published here stamps once the card has grown in`() {
        val seen = mutableListOf<Boolean>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { seen += rememberTicketLanded(first, stamps = true) }
        composeRule.mainClock.advanceTimeByFrame()
        assertFalse(seen.last())
        composeRule.mainClock.advanceTimeBy(ContainerTransform.enterDurationMillis.toLong() + FRAME_MS)
        assertTrue(seen.last())
    }

    @Test
    fun `a restored ticket does not stamp again`() {
        val restoration = StateRestorationTester(composeRule)
        val seen = mutableListOf<Boolean>()
        composeRule.mainClock.autoAdvance = false
        restoration.setContent { seen += rememberTicketLanded(first, stamps = true) }
        composeRule.mainClock.advanceTimeBy(ContainerTransform.enterDurationMillis.toLong() + FRAME_MS)
        assertTrue(seen.last())
        seen.clear()
        composeRule.mainClock.autoAdvance = true
        restoration.emulateSavedInstanceStateRestore()
        assertTrue(seen.isNotEmpty() && seen.all { it })
    }

    @Test
    fun `another fresh code stamps again`() {
        var code by mutableStateOf(first)
        val seen = mutableListOf<Pair<ShareCode, Boolean>>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { seen += code to rememberTicketLanded(code, stamps = true) }
        composeRule.mainClock.advanceTimeBy(ContainerTransform.enterDurationMillis.toLong() + FRAME_MS)
        composeRule.mainClock.autoAdvance = true
        code = second
        composeRule.waitForIdle()
        assertEquals(second to false, seen.first { it.first == second })
    }

    private companion object {
        const val FRAME_MS = 32L
    }
}
