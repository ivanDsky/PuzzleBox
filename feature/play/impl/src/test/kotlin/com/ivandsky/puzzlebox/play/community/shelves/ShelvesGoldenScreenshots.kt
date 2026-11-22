package com.ivandsky.puzzlebox.play.community.shelves

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shelves home on the phone, light and dark, one capture per state: the real list (top and the game shelves below),
 * few puzzles, a counted list (Spotlight and Most played), the longest copy, the empty hero, loading, offline and
 * refused. Goldens in `snapshots/community-shelves/`; untracked review captures, never a gate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ShelvesGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `real light`() = capture("real", sampleShelves(30), dark = false)
    @Test fun `real dark`() = capture("real", sampleShelves(30), dark = true)

    @Test fun `few light`() = capture("few", sampleShelves(8), dark = false)
    @Test fun `few dark`() = capture("few", sampleShelves(8), dark = true)

    @Test fun `counted light`() = capture("counted", sampleShelves(30, counted = true), dark = false)
    @Test fun `counted dark`() = capture("counted", sampleShelves(30, counted = true), dark = true)

    @Test fun `longest light`() = capture("longest", sampleShelves(30, counted = true, longest = true), dark = false)
    @Test fun `longest dark`() = capture("longest", sampleShelves(30, counted = true, longest = true), dark = true)

    @Test fun `empty light`() = capture("empty", ShelvesUiState.Empty(SampleEntries), dark = false)
    @Test fun `empty dark`() = capture("empty", ShelvesUiState.Empty(SampleEntries), dark = true)

    @Test fun `loading light`() = capture("loading", ShelvesUiState.Loading, dark = false)
    @Test fun `loading dark`() = capture("loading", ShelvesUiState.Loading, dark = true)

    @Test fun `offline light`() = capture("offline", ShelvesUiState.Offline(SampleEntries), dark = false)
    @Test fun `offline dark`() = capture("offline", ShelvesUiState.Offline(SampleEntries), dark = true)

    @Test fun `refused light`() = capture("refused", ShelvesUiState.Refused(SampleEntries), dark = false)
    @Test fun `refused dark`() = capture("refused", ShelvesUiState.Refused(SampleEntries), dark = true)

    private fun capture(name: String, state: ShelvesUiState, dark: Boolean) {
        rule.showGolden(dark) {
            CommunityShelvesScreen(state, ShelvesActions(onEnterCode = {}, onOpen = {}, onRetry = {}), phone = true, Modifier.fillMaxSize())
        }
        rule.settleAndCapture("community-shelves", name, "phone", dark)
    }
}
