package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game's loading frame, phone and landscape tablet, light and dark: on the tablet the skeleton sits centred in the
 * board pane beside the empty side pane, where the loaded board lands. Goldens in `snapshots/game-loading/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class GameLoadingGoldenScreenshotsPhone : GameLoadingGoldenScreenshots("phone", PlayLayout.SinglePane)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class GameLoadingGoldenScreenshotsTablet : GameLoadingGoldenScreenshots("tablet", PlayLayout.TwoPane)

abstract class GameLoadingGoldenScreenshots(private val device: String, private val layout: PlayLayout) {

    @get:Rule
    val rule = createComposeRule()

    @Test fun `light`() = capture(dark = false)

    @Test fun `dark`() = capture(dark = true)

    private fun capture(dark: Boolean) {
        rule.showGolden(dark) { GameLoading(layout, onBack = {}, todaysDaily = true) }
        rule.settleAndCapture("game-loading", "game-loading", device, dark)
    }
}
