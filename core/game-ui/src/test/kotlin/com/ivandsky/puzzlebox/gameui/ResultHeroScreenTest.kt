package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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

    private fun setContent(widthDp: Int, heightDp: Int, writeFailed: Boolean = false, facts: ResultHeroFacts = heroSampleFacts()) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
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

    private fun assertCardsEqualHeight() {
        val hero = composeRule.onNodeWithTag("result.hero.card").getUnclippedBoundsInRoot()
        val side = composeRule.onNodeWithTag("result.side.card").getUnclippedBoundsInRoot()
        assertEquals("hero ${hero.height} vs side ${side.height}", hero.height.value, side.height.value, 1f)
    }

    @Test
    fun tablet_cards_are_equal_height_when_the_side_card_is_the_shorter() {
        // A play-again has no streak and no rank: the side card is the chart block alone.
        setContent(1280, 800, facts = heroSampleFacts(playAgain = true))
        val hero = composeRule.onNodeWithTag("result.hero.card").getUnclippedBoundsInRoot()
        val verdict = composeRule.onNodeWithTag("result.side.verdict").getUnclippedBoundsInRoot()
        assertTrue("premise: side content ends at ${verdict.bottom}, inside the hero card (${hero.bottom})", verdict.bottom < hero.bottom)
        assertCardsEqualHeight()
    }

    @Test
    fun tablet_cards_are_equal_height_when_the_side_card_is_the_taller() {
        setContent(1280, 800)
        val rank = composeRule.onNodeWithTag("result.side.rank").getUnclippedBoundsInRoot()
        val share = composeRule.onNodeWithTag("result.share").getUnclippedBoundsInRoot()
        assertTrue("premise: hero content ends at ${share.bottom}, above the rank row (${rank.bottom})", share.bottom < rank.bottom)
        assertCardsEqualHeight()
    }

    @Test
    fun tablet_cards_stay_equal_height_after_the_chart_scope_switches() {
        setContent(1280, 800)
        composeRule.onNodeWithTag("result.side.chip").performClick()
        composeRule.waitForIdle()
        assertCardsEqualHeight()
        composeRule.onNodeWithTag("result.side.chip").performClick()
        composeRule.waitForIdle()
        assertCardsEqualHeight()
    }

    @Test
    fun tablet_cards_follow_the_taller_down_when_its_content_shrinks() {
        var withActions by mutableStateOf(true)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                val facts = heroSampleFacts(playAgain = true)
                ResultHeroScreen(
                    facts = facts,
                    modifier = Modifier.requiredSize(1280.dp, 800.dp),
                    brand = brand,
                    actions = if (withActions) ({ ResultHeroActions(facts.primary, onPrimary = {}, onShare = {}, onPlayAgain = {}) }) else null,
                )
            }
        }
        val tall = composeRule.onNodeWithTag("result.hero.card").getUnclippedBoundsInRoot().height
        assertCardsEqualHeight()
        withActions = false
        composeRule.waitForIdle()
        val short = composeRule.onNodeWithTag("result.hero.card").getUnclippedBoundsInRoot().height
        assertTrue("the pair must shrink with its taller card: $tall -> $short", short < tall)
        assertCardsEqualHeight()
    }

    @Test
    fun phone_cards_keep_their_own_heights() {
        setContent(411, 891)
        val hero = composeRule.onNodeWithTag("result.hero.card").getUnclippedBoundsInRoot()
        val side = composeRule.onNodeWithTag("result.side.card").getUnclippedBoundsInRoot()
        assertTrue("one column must not stretch: hero ${hero.height} side ${side.height}", hero.height != side.height)
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
