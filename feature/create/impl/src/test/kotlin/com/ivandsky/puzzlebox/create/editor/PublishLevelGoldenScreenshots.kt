package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 96: the publish details with the level control, phone and tablet, light and dark: the proposal followed (Hard chosen,
 * "Proposed: Hard") and an author's own pick (Easy chosen over the same proposal). The details sit under the Public or
 * Link only picker, the way the Studio shows them. Rendered with `-Proborazzi.record=true` into
 * `snapshots/publish-level/` (untracked review captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class PublishLevelGoldenScreenshotsPhone : PublishLevelGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class PublishLevelGoldenScreenshotsTablet : PublishLevelGoldenScreenshots("tablet")

abstract class PublishLevelGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `followed light`() = capture("followed", FOLLOWED, dark = false)
    @Test fun `followed dark`() = capture("followed", FOLLOWED, dark = true)

    @Test fun `own pick light`() = capture("own-pick", OWN_PICK, dark = false)
    @Test fun `own pick dark`() = capture("own-pick", OWN_PICK, dark = true)

    private fun capture(name: String, level: LevelChoice, dark: Boolean) {
        rule.showGolden(dark) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                PublishDetails(
                    title = "Sunday Sudoku",
                    details = DETAILS,
                    signedIn = true,
                    onRename = {},
                    onRollTitle = { "Amber Falcon" },
                    onAuthorName = {},
                    onNote = {},
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    level = level,
                    onLevel = {},
                )
            }
        }
        rule.settleAndCapture("publish-level", name, device, dark)
    }

    private companion object {
        val DETAILS = PublishSession.Details(
            authorName = "Ivan",
            note = "Start from the corner.",
            visibility = PublishVisibility.Choice(Visibility.Public),
            levelOffered = true,
        )
        val FOLLOWED = LevelChoice(proposed = Level.Hard, chosen = Level.Hard)
        val OWN_PICK = LevelChoice(proposed = Level.Hard, chosen = Level.Easy)
    }
}
