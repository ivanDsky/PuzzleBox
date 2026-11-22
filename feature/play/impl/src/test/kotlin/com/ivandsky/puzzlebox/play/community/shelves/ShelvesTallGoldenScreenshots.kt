package com.ivandsky.puzzlebox.play.community.shelves

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole shelves home on a tall phone, so every shelf and its Make card is in one capture: the real list and the counted
 * one. Goldens in `snapshots/community-shelves/`; untracked review captures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h3200dp-420dpi")
class ShelvesTallGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `real light`() = capture("real-tall", sampleShelves(30), dark = false)
    @Test fun `real dark`() = capture("real-tall", sampleShelves(30), dark = true)

    @Test fun `counted light`() = capture("counted-tall", sampleShelves(30, counted = true), dark = false)
    @Test fun `counted dark`() = capture("counted-tall", sampleShelves(30, counted = true), dark = true)

    private fun capture(name: String, state: ShelvesUiState, dark: Boolean) {
        rule.showGolden(dark) {
            CommunityShelvesScreen(state, ShelvesActions(onEnterCode = {}, onOpen = {}, onRetry = {}), phone = true, Modifier.fillMaxSize())
        }
        rule.settleAndCapture("community-shelves", name, "phone", dark)
    }
}
