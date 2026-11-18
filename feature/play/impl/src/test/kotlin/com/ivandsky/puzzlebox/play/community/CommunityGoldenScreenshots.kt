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
 * The community New today section, phone and tablet, light and dark: loaded (menu closed, nothing filtered, rows
 * with and without a difficulty), filtered (the active line), menu open, empty, and offline. Goldens in
 * `snapshots/community/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class CommunityGoldenScreenshotsPhone : CommunityGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class CommunityGoldenScreenshotsTablet : CommunityGoldenScreenshots("tablet")

abstract class CommunityGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `loaded light`() = capture("loaded", LOADED, dark = false)
    @Test fun `loaded dark`() = capture("loaded", LOADED, dark = true)

    @Test fun `filtered light`() = capture("filtered", FILTERED, dark = false)
    @Test fun `filtered dark`() = capture("filtered", FILTERED, dark = true)

    @Test fun `menu open light`() = capture("menu-open", FILTERED, dark = false, menuOpen = true)
    @Test fun `menu open dark`() = capture("menu-open", FILTERED, dark = true, menuOpen = true)

    @Test fun `empty light`() = capture("empty", EMPTY, dark = false)
    @Test fun `empty dark`() = capture("empty", EMPTY, dark = true)

    @Test fun `offline light`() = capture("offline", OFFLINE, dark = false)
    @Test fun `offline dark`() = capture("offline", OFFLINE, dark = true)

    private fun capture(name: String, state: CommunityState, dark: Boolean, menuOpen: Boolean = false) {
        rule.showGolden(dark) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl)) {
                communitySection(state, CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {}))
            }
        }
        if (menuOpen) {
            rule.onNodeWithTag("community.filter.menu").performClick()
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithTag("community.filter.game.all").assertIsDisplayed()
        }
        rule.settleAndCapture("community", name, device, dark)
    }

    private companion object {
        val ZIP = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
        val TANGO = GameBrand(AccentSlot.Orange, GameGlyphs.Tango)
        val QUEENS = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)

        val GAMES = listOf(
            GameChoice(GameId("zip"), "Zip"),
            GameChoice(GameId("tango"), "Tango"),
            GameChoice(GameId("queens"), "Queens"),
        )

        fun row(code: String, game: String, brand: GameBrand, title: String, level: Difficulty.Level?, age: String) =
            CommunityRowModel(code(code), game, brand, title, author = "Maya", difficulty = level, age = age)

        val BASE = CommunityState(games = GAMES)

        val LOADED = BASE.copy(
            phase = CommunityPhase.Loaded(
                listOf(
                    row("K7Q2MX", "Zip", ZIP, "Spiral staircase", Difficulty.Level.Hard, "5 min ago"),
                    row("P4R8NT", "Tango", TANGO, "Sun and moon, but only after a very long title that wraps", Difficulty.Level.Expert, "2 h ago"),
                    row("W9C3HB", "Queens", QUEENS, "Crown jewels", null, "13 h ago"),
                ),
            ),
        )
        val FILTERED = BASE.copy(
            game = GameId("zip"),
            difficulty = DifficultyChoice.Hard,
            phase = CommunityPhase.Loaded(listOf(row("K7Q2MX", "Zip", ZIP, "Spiral staircase", Difficulty.Level.Hard, "5 min ago"))),
        )
        val EMPTY = BASE.copy(phase = CommunityPhase.Loaded(emptyList()))
        val OFFLINE = BASE.copy(phase = CommunityPhase.Offline)
    }
}
