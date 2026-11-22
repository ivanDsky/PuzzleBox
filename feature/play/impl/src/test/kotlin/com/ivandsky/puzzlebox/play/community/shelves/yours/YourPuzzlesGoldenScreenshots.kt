package com.ivandsky.puzzlebox.play.community.shelves.yours

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.play.CommunityPanes
import com.ivandsky.puzzlebox.play.CustomSlot
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.ShelvesListPaneWidth
import com.ivandsky.puzzlebox.play.previewYoursSlot
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Your puzzles page, light and dark: with the slot's list (here the preview Yours list) and empty. The tablet shows it
 * in the 600dp list pane. Goldens in `snapshots/community-shelves/`; untracked review captures, never a gate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class YourPuzzlesGoldenScreenshotsPhone : YourPuzzlesGoldenScreenshots(tablet = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class YourPuzzlesGoldenScreenshotsTablet : YourPuzzlesGoldenScreenshots(tablet = true)

abstract class YourPuzzlesGoldenScreenshots(private val tablet: Boolean) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `content light`() = capture("yours-content", previewYoursSlot(count = 3), dark = false)
    @Test fun `content dark`() = capture("yours-content", previewYoursSlot(count = 3), dark = true)

    @Test fun `empty light`() = capture("yours-empty", previewYoursSlot(count = 0), dark = false)
    @Test fun `empty dark`() = capture("yours-empty", previewYoursSlot(count = 0), dark = true)

    private fun capture(name: String, slot: CustomSlot, dark: Boolean) {
        rule.showGolden(dark) {
            if (tablet) {
                CommunityPanes(
                    default = { null },
                    list = { size, _ -> YourPuzzlesPage(slot.yours, onBack = {}, modifier = size) },
                    empty = { Text("Pick a puzzle to see it here") },
                    detail = { _, _ -> },
                    listPaneWidth = ShelvesListPaneWidth,
                )
            } else {
                YourPuzzlesPage(slot.yours, onBack = {}, modifier = Modifier.fillMaxSize())
            }
        }
        rule.settleAndCapture("community-shelves", name, if (tablet) "tablet" else "phone", dark)
    }
}
