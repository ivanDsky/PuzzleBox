package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A community puzzle's hero: the daily's hero model with Hints as "0", the community chips in place of Today and Average,
 * its own second line and no daily-only side parts; and the page puts the ranks list in the side card on a tablet and
 * under the hero on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultHeroCommunityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rankChip = ResultHeroChip("result.hero.chip.rank", "#1", "of 8")
    private val authorChip = ResultHeroChip("result.hero.chip.author", "1:42", "Author")

    private fun community(hints: Int = 0, playAgain: Boolean = false) = heroSampleFacts(
        verdict = if (playAgain) RecordVerdict.PlayAgain else RecordVerdict.NewBest,
        hintsUsed = hints,
        game = "Queens 8×8 · Test",
        number = null,
        playAgain = playAgain,
        doneToday = null,
        total = null,
        rank = ResultRank.Hidden,
        primary = ResultPrimaryAction.Done,
    ).copy(dateLine = "by PuzzleBox", insights = null, samePuzzle = SamePuzzleSolves.None, community = ResultCommunity(listOf(rankChip, authorChip)))

    @Test
    fun `hints read 0 not None and the community chips follow in place of Today and Average`() {
        val model = resultHeroModel(community())
        assertEquals(listOf("0", "#1", "1:42"), model.chips.map { it.value })
        assertEquals(listOf("Hints", "of 8", "Author"), model.chips.map { it.label })
    }

    @Test
    fun `a daily keeps None for no hints`() {
        assertEquals("None", resultHeroModel(heroSampleFacts(hintsUsed = 0)).chips.first().value)
    }

    @Test
    fun `the header is the puzzle name once with the by line, and no daily side parts`() {
        val model = resultHeroModel(community())
        assertEquals("Queens 8×8 · Test", model.title)
        assertEquals("by PuzzleBox", model.dateLine)
        assertNull(model.streak)
        assertNull(model.spread)
        assertNull(model.rank)
        assertTrue(model.moments.isEmpty())
    }

    @Test
    fun `a first solve shows no compare`() {
        val model = resultHeroModel(community().copy(previousBest = null))
        assertNull(model.compareColumns)
        assertNull(model.bestLine)
    }

    @Test
    fun `a solve with earlier solves compares with the previous one`() {
        val same = SamePuzzleSolves(previous = com.ivandsky.puzzlebox.game.BestScore(120_000L, 0), previousFromLinkedIn = false, best = null, columns = true)
        val model = resultHeroModel(community().copy(samePuzzle = same))
        assertEquals("2:00", model.compareColumns?.previous?.value)
    }

    @Test
    fun `a play again says Practice`() {
        val model = resultHeroModel(community(playAgain = true))
        assertNull(model.pill)
        assertEquals("Practice", model.bestLine?.substringBefore(" ·"))
    }

    private fun setContent(widthDp: Int, heightDp: Int) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultHeroScreen(
                    facts = community(),
                    modifier = Modifier.requiredSize(widthDp.dp, heightDp.dp),
                    brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
                    actions = { Text("actions") },
                    side = { Box(Modifier.height(120.dp).testTag("ranks")) { Text("ranks") } },
                )
            }
        }
    }

    @Test
    fun `tablet puts the ranks in the side card right of the hero, both cards one height`() {
        setContent(1280, 800)
        val hero = composeRule.onNodeWithTag("result.hero.card").getUnclippedBoundsInRoot()
        val side = composeRule.onNodeWithTag("result.side.card").getUnclippedBoundsInRoot()
        val ranks = composeRule.onNodeWithTag("ranks").getUnclippedBoundsInRoot()
        assertTrue("ranks ${ranks.left} right of hero ${hero.right}", ranks.left >= hero.right)
        assertEquals("hero ${hero.height} vs side ${side.height}", hero.height.value, side.height.value, 1f)
    }

    @Test
    fun `phone puts the ranks under the hero`() {
        setContent(411, 891)
        val hero = composeRule.onNodeWithTag("result.hero.card").getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("ranks").assertIsDisplayed()
        val ranks = composeRule.onNodeWithTag("ranks").getUnclippedBoundsInRoot()
        assertTrue("ranks ${ranks.top} under hero ${hero.bottom}", ranks.top >= hero.bottom)
    }
}
