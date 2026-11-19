package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.GameCardModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The game page's stats card and its "See all stats" row: there when the stats page is on, gone when it is dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class GameStatsCardSeeAllTest {

    @get:Rule
    val rule = createComposeRule()

    private val zip = GameId("zip")
    private val today = LocalDate.parse("2026-11-05")
    private val row = LibraryRowModel(
        card = GameCardModel(zip, "Zip", GameBrand.Neutral, status = null, rankOrder = RankOrder.TimeThenHints),
        today = 41,
        progress = null,
        stats = GameStats(1, 1, 12, 12, 30_000L, today, BestScore(30_000L, 0), RankOrder.TimeThenHints),
        rules = gameRules(zip),
    )

    @Test
    fun `with an action the card ends in See all stats, which calls it`() {
        var opened = 0
        rule.setContent { PuzzleBoxTheme { GameStatsCard(row, onSeeAllStats = { opened++ }) } }

        rule.onNodeWithTag("library.stats.seeAll").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `without an action the card has no See all stats row`() {
        rule.setContent { PuzzleBoxTheme { GameStatsCard(row) } }

        rule.onNodeWithTag("library.stats").assertIsDisplayed()
        rule.onNodeWithTag("library.stats.seeAll").assertDoesNotExist()
    }

    @Test
    fun `an empty card offers it too`() {
        val empty = row.copy(stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints))
        rule.setContent { PuzzleBoxTheme { GameStatsCard(empty, onSeeAllStats = {}) } }

        rule.onNodeWithTag("library.stats.seeAll").assertIsDisplayed()
    }
}
