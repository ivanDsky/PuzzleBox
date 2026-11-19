package com.ivandsky.puzzlebox.play.random

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Random sheet's body with the filter chips on, phone and tablet, light and dark: New today, a Zip and Hard pick, Past puzzles,
 * a pick running, New today at font scale 1.3, and the Game page that replaces the body. Record-only, in `snapshots/random-filters/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class RandomFilterGoldenScreenshotsPhone : RandomFilterGoldenScreenshots("phone", PlayLayout.SinglePane)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class RandomFilterGoldenScreenshotsTablet : RandomFilterGoldenScreenshots("tablet", PlayLayout.TwoPane)

abstract class RandomFilterGoldenScreenshots(private val device: String, private val layout: PlayLayout) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `new today light`() = capture("new-today", NEW_TODAY, dark = false)
    @Test fun `new today dark`() = capture("new-today", NEW_TODAY, dark = true)

    @Test fun `picked light`() = capture("picked", PICKED, dark = false)
    @Test fun `picked dark`() = capture("picked", PICKED, dark = true)

    @Test fun `past light`() = capture("past", PAST, dark = false)
    @Test fun `past dark`() = capture("past", PAST, dark = true)

    @Test fun `busy light`() = capture("busy", NEW_TODAY.copy(phase = RandomPhase.Picking), dark = false)
    @Test fun `busy dark`() = capture("busy", NEW_TODAY.copy(phase = RandomPhase.Picking), dark = true)

    @Test fun `large text light`() = capture("large-text", NEW_TODAY, dark = false, fontScale = 1.3f)
    @Test fun `large text dark`() = capture("large-text", NEW_TODAY, dark = true, fontScale = 1.3f)

    @Test fun `game page light`() = capture("game-page", PICKED, dark = false, page = RandomFilterPage.Game)
    @Test fun `game page dark`() = capture("game-page", PICKED, dark = true, page = RandomFilterPage.Game)

    private fun capture(name: String, state: RandomPuzzleState, dark: Boolean, fontScale: Float = 1f, page: RandomFilterPage? = null) {
        rule.showGolden(dark) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Surface(Modifier.widthIn(max = SHEET_WIDTH)) {
                    if (page == null) {
                        RandomPuzzleContent(state, GAMES, layout, ACTIONS, filterSheets = true)
                    } else {
                        RandomFilterPageContent(page, state, GAMES, ACTIONS, onBack = {})
                    }
                }
            }
        }
        rule.settleAndCapture("random-filters", name, device, dark)
    }

    private companion object {
        val SHEET_WIDTH = 640.dp
        val ACTIONS = RandomPuzzleActions({}, {}, {}, {}, {})
        val GAMES = listOf(
            RandomGame(GameId("zip"), "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip)),
            RandomGame(GameId("tango"), "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango)),
            RandomGame(GameId("queens"), "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens)),
        )
        val NEW_TODAY = RandomPuzzleState(RandomSource.Community, game = null, unsolvedOnly = true, difficulty = null, phase = RandomPhase.Idle)
        val PICKED = NEW_TODAY.copy(game = GameId("zip"), difficulty = DifficultyChoice.Hard)
        val PAST = NEW_TODAY.copy(source = RandomSource.Past)
    }
}
