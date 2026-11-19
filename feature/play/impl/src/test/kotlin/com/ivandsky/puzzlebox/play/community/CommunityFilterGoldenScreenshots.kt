package com.ivandsky.puzzlebox.play.community

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The community New today section with the filter chips on, phone and tablet, light and dark: the chips with the count,
 * a game and a difficulty picked, the Game sheet open, and no matches. Record-only, in `snapshots/community-filters/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class CommunityFilterGoldenScreenshotsPhone : CommunityFilterGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class CommunityFilterGoldenScreenshotsTablet : CommunityFilterGoldenScreenshots("tablet")

abstract class CommunityFilterGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `chips light`() = capture("chips", LOADED, dark = false)
    @Test fun `chips dark`() = capture("chips", LOADED, dark = true)

    @Test fun `picked light`() = capture("picked", PICKED, dark = false)
    @Test fun `picked dark`() = capture("picked", PICKED, dark = true)

    @Test fun `game sheet light`() = capture("game-sheet", PICKED, dark = false, openChip = "community.filter.game")
    @Test fun `game sheet dark`() = capture("game-sheet", PICKED, dark = true, openChip = "community.filter.game")

    @Test fun `no match light`() = capture("no-match", NO_MATCH, dark = false)
    @Test fun `no match dark`() = capture("no-match", NO_MATCH, dark = true)

    private fun capture(name: String, state: CommunityState, dark: Boolean, openChip: String? = null) {
        rule.showGolden(dark) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl)) {
                communitySection(state, CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {}))
            }
        }
        if (openChip != null) {
            rule.onNodeWithTag(openChip).performClick()
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithTag("$openChip.sheet").assertIsDisplayed()
        }
        rule.settleAndCapture("community-filters", name, device, dark)
    }

    private companion object {
        val ZIP = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
        val TANGO = GameBrand(AccentSlot.Orange, GameGlyphs.Tango)
        val QUEENS = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

        val GAMES = listOf(
            GameChoice(GameId("zip"), "Zip", ZIP),
            GameChoice(GameId("tango"), "Tango", TANGO),
            GameChoice(GameId("queens"), "Queens", QUEENS),
        )

        fun row(code: String, game: String, brand: GameBrand, title: String, level: Difficulty.Level?, age: String) =
            CommunityRowModel(code(code), game, brand, title, author = "Maya", difficulty = level, age = age)

        val ZIP_ROW = row("K7Q2MX", "Zip", ZIP, "Spiral staircase", Difficulty.Level.Hard, "5 min ago")

        val BASE = CommunityState(games = GAMES)

        val LOADED = BASE.copy(
            phase = CommunityPhase.Loaded(
                listOf(
                    ZIP_ROW,
                    row("P4R8NT", "Tango", TANGO, "Sun and moon", Difficulty.Level.Expert, "2 h ago"),
                    row("W9C3HB", "Queens", QUEENS, "Crown jewels", null, "13 h ago"),
                ),
            ),
        )
        val PICKED = BASE.copy(game = GameId("zip"), difficulty = DifficultyChoice.Hard, phase = CommunityPhase.Loaded(listOf(ZIP_ROW)))
        val NO_MATCH = BASE.copy(game = GameId("tango"), difficulty = DifficultyChoice.Easy, phase = CommunityPhase.Loaded(emptyList()))
    }
}
