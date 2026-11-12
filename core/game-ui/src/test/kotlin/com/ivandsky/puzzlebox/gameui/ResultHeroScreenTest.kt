package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hero page's layout by width: both cards stack on a phone and sit side by side on a
 * tablet; and the failed-write notice shows above them and retries.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultHeroScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
    private var retried = 0

    private fun setContent(widthDp: Int, heightDp: Int, writeFailed: Boolean = false) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                val facts = heroSampleFacts()
                ResultHeroScreen(
                    facts = facts,
                    onSignIn = {},
                    modifier = Modifier.requiredSize(widthDp.dp, heightDp.dp),
                    brand = brand,
                    onRetryWrite = if (writeFailed) ({ retried++ }) else null,
                    actions = { ResultHeroActions(facts.primary, onPrimary = {}, onShare = {}, onPlayAgain = {}) },
                )
            }
        }
    }

    @Test
    fun phone_width_stacks_the_side_card_below_the_hero_card() {
        setContent(411, 891)
        val hero = composeRule.onNodeWithTag("result.hero.time").getUnclippedBoundsInRoot()
        val side = composeRule.onNodeWithTag("result.side.streak").getUnclippedBoundsInRoot()
        assertTrue("side ${side.top} must sit below hero ${hero.bottom}", side.top >= hero.bottom)
    }

    @Test
    fun tablet_width_puts_the_side_card_to_the_right_of_the_hero_card() {
        setContent(1280, 800)
        val hero = composeRule.onNodeWithTag("result.hero.time").getUnclippedBoundsInRoot()
        val side = composeRule.onNodeWithTag("result.side.streak").getUnclippedBoundsInRoot()
        assertTrue("side ${side.left} must sit right of hero ${hero.right}", side.left >= hero.right)
    }

    @Test
    fun the_failed_write_notice_shows_and_retries() {
        setContent(300, 460, writeFailed = true)
        composeRule.onNodeWithTag("result.write_failed").assertIsDisplayed()
        composeRule.onNodeWithTag("result.retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun no_notice_shows_while_the_write_is_fine() {
        setContent(300, 460)
        composeRule.onNodeWithTag("result.hero.screen").assertIsDisplayed()
        composeRule.onNodeWithTag("result.write_failed").assertDoesNotExist()
    }

    private fun setContentWithReplay(widthDp: Int, heightDp: Int) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                val facts = heroSampleFacts()
                ResultHeroScreen(
                    facts = facts,
                    onSignIn = {},
                    modifier = Modifier.requiredSize(widthDp.dp, heightDp.dp),
                    brand = brand,
                    actions = { ResultHeroActions(facts.primary, onPrimary = {}, onShare = {}, onPlayAgain = {}) },
                    replay = { Box(Modifier.fillMaxWidth().height(400.dp).testTag("result.replay.slot")) },
                )
            }
        }
    }

    @Test
    fun phone_shows_the_actions_without_scrolling_with_the_replay_slot_filled() {
        setContentWithReplay(411, 891)
        composeRule.onNodeWithTag("result.share").assertIsDisplayed()
        composeRule.onNodeWithTag("result.playAgain").assertIsDisplayed()
        val slot = composeRule.onNodeWithTag("result.replay.slot").assertExists().getUnclippedBoundsInRoot()
        assertTrue("slot bottom ${slot.bottom} must lie past the 891 dp viewport", slot.bottom > 891.dp)
    }

    @Test
    fun phone_draws_the_replay_slot_below_the_side_card() {
        setContentWithReplay(411, 891)
        val side = composeRule.onNodeWithTag("result.side.streak").getUnclippedBoundsInRoot()
        val slot = composeRule.onNodeWithTag("result.replay.slot").getUnclippedBoundsInRoot()
        assertTrue("slot ${slot.top} must sit below side ${side.bottom}", slot.top >= side.bottom)
    }

    @Test
    fun tablet_draws_the_replay_slot_below_both_columns() {
        setContentWithReplay(1280, 800)
        val side = composeRule.onNodeWithTag("result.side.streak").getUnclippedBoundsInRoot()
        val share = composeRule.onNodeWithTag("result.share").getUnclippedBoundsInRoot()
        val slot = composeRule.onNodeWithTag("result.replay.slot").getUnclippedBoundsInRoot()
        assertTrue("slot ${slot.top} must sit below side ${side.bottom} and share ${share.bottom}", slot.top >= maxOf(side.bottom, share.bottom))
    }

    @Test
    fun a_null_slot_composes_no_replay_node() {
        setContent(411, 891)
        composeRule.onNodeWithTag("result.replay.slot").assertDoesNotExist()
    }
}
