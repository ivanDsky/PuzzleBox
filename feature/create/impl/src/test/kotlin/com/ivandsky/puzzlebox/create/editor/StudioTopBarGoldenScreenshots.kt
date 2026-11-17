package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Studio's top bar with the autosave indicator in each state: "Saving…" while an edit waits
 * to be stored, "✓ Saved" once it is, beside a long subtitle and the widest verdict pill. Light and
 * dark, phone and tablet, and the narrowest phone. Goldens in `snapshots/studio/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class StudioTopBarGoldenScreenshotsPhone : StudioTopBarGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class StudioTopBarGoldenScreenshotsTablet : StudioTopBarGoldenScreenshots("tablet")

/** The narrowest phone, where the save state must still lay out whole. Adds the Medium pill, which fits beside it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class StudioTopBarGoldenScreenshotsNarrow : StudioTopBarGoldenScreenshots("phone-360") {

    @Test fun `medium pill light`() = capture("topbar-medium", saving = false, dark = false, pill = MEDIUM_PILL)
    @Test fun `medium pill dark`() = capture("topbar-medium", saving = false, dark = true, pill = MEDIUM_PILL)

    private companion object {
        val MEDIUM_PILL: @Composable () -> Unit = {
            VerdictPill(
                verdict = Verdict.Unique(Solution(listOf(1, 7, 8, 14)), Difficulty(Difficulty.Level.Medium, score = null, hardestRung = null)),
                onClick = {},
            )
        }
    }
}

abstract class StudioTopBarGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `saving light`() = capture("topbar-saving", saving = true, dark = false)
    @Test fun `saving dark`() = capture("topbar-saving", saving = true, dark = true)

    @Test fun `saved light`() = capture("topbar-saved", saving = false, dark = false)
    @Test fun `saved dark`() = capture("topbar-saved", saving = false, dark = true)

    protected fun capture(name: String, saving: Boolean, dark: Boolean, pill: @Composable () -> Unit = WIDEST_PILL) {
        rule.showGolden(dark) {
            StudioTopBar(
                title = "Castle",
                subtitle = "Queens 12×12 · Unique · needs guessing",
                saving = saving,
                onBack = {},
                onRename = {},
                onDuplicate = {},
                onDelete = {},
                pill = pill,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        rule.settleAndCapture("studio", name, device, dark)
    }

    private companion object {
        val WIDEST_PILL: @Composable () -> Unit = {
            VerdictPill(verdict = Verdict.Unique(Solution(listOf(1, 7, 8, 14)), needsGuessing = true), onClick = {})
        }
    }
}
