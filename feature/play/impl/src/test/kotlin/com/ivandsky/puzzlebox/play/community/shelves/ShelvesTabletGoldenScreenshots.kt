package com.ivandsky.puzzlebox.play.community.shelves

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.play.CommunityPanes
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.ShelvesListPaneWidth
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shelves home on the tablet: the 600dp list pane and the empty detail pane beside it, for the real list, the empty
 * hero, loading and offline, light and dark. Goldens in `snapshots/community-shelves/`; untracked review captures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ShelvesTabletGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `real light`() = capture("real", sampleShelves(30), dark = false)
    @Test fun `real dark`() = capture("real", sampleShelves(30), dark = true)

    @Test fun `counted light`() = capture("counted", sampleShelves(30, counted = true), dark = false)

    @Test fun `empty light`() = capture("empty", ShelvesUiState.Empty(SampleEntries), dark = false)
    @Test fun `empty dark`() = capture("empty", ShelvesUiState.Empty(SampleEntries), dark = true)

    @Test fun `loading light`() = capture("loading", ShelvesUiState.Loading, dark = false)

    @Test fun `offline dark`() = capture("offline", ShelvesUiState.Offline(SampleEntries), dark = true)

    private fun capture(name: String, state: ShelvesUiState, dark: Boolean) {
        rule.showGolden(dark) {
            CommunityPanes(
                default = { null },
                list = { size, _ ->
                    CommunityShelvesScreen(state, ShelvesActions(onEnterCode = {}, onOpen = {}, onRetry = {}), phone = false, size)
                },
                empty = { Text("Pick a puzzle to see it here") },
                detail = { _, _ -> },
                listPaneWidth = ShelvesListPaneWidth,
            )
        }
        rule.settleAndCapture("community-shelves", name, "tablet", dark)
    }
}
