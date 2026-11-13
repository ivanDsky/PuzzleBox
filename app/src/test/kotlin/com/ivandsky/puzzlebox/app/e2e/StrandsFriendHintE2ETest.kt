package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.onAllNodesWithTag
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.StrandsWoods.NON_THEME
import com.ivandsky.puzzlebox.app.e2e.StrandsWoods.awaitBoard
import com.ivandsky.puzzlebox.app.e2e.StrandsWoods.tapWord
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Strands friend line's hint (R2, F): on a friend's custom board opened by code, two
 * non-theme words fill two of the hint's dots and bank nothing; the third banks a hint, so the
 * hint button lights with its count badge.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// A phone-sized window: the default Robolectric screen would push the 8-row board off it.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class StrandsFriendHintE2ETest {

    private val world = FriendWorld(StrandsWoods.flags)

    @After
    fun close() = world.close()

    @Test
    fun `three non-theme words on a friend's Strands board light the hint button`() = shellRobot(graph = world.graph) {
        val code = world.publishAsAuthor(this, StrandsWoods.board, "Woods")
        world.signInFriend()
        tapTab(PlayKey)
        openByCode(code)
        tap("friend.card.play")
        val controller = awaitBoard(PlaySlot.Custom(StrandsWoods.id, code.value))
        awaitTag("strands.hint")

        NON_THEME.dropLast(1).forEach { tapWord(it) }
        assertEquals("two words fill two dots", 2, nodes("strands.hint_dot.on"))
        assertEquals("no hint is banked yet", 0, nodes("strands.hint_badge"))
        assertFalse("the hint is not available yet", rule.runOnIdle { controller.hint.available })

        tapWord(NON_THEME.last())
        assertEquals("each tapped word counts", NON_THEME.size, rule.runOnIdle { controller.session?.state?.nonTheme?.size })
        awaitTag("strands.hint_badge")
        assertTrue("the third word banks a hint", rule.runOnIdle { controller.hint.available })
    }

    private fun ShellRobot.nodes(tag: String): Int =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
}
