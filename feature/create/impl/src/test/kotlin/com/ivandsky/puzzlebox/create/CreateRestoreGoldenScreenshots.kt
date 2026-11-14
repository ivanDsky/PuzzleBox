package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Create after the first cloud restore (ticket 66), on the golden phone, light and dark, as settled frames: the
 * restoring card, and the drafts it gives way to once the restore ends with items. Goldens in `snapshots/restore/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class CreateRestoreGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(CreateUiState())

    @Test fun `restoring light`() = restoring(dark = false)
    @Test fun `restoring dark`() = restoring(dark = true)

    @Test fun `restored light`() = restored(dark = false)
    @Test fun `restored dark`() = restored(dark = true)

    private fun show(initial: CreateUiState, dark: Boolean) {
        state = initial
        rule.showGolden(dark) {
            CreateScreen(
                state, onNew = { _, _ -> }, onOpen = {}, onQuery = {}, onFilter = {}, onTab = {}, onOpenPublished = {},
                onDuplicate = {}, onDelete = {}, onUndoDelete = {},
            )
        }
    }

    private fun restoring(dark: Boolean) {
        show(CreateUiState(loading = false, games = PreviewGames, restoring = true), dark)
        rule.settleAndCapture("restore", "create-restoring", "phone", dark)
    }

    // The restore ends with drafts: the screen is first drawn restoring, then the drafts arrive and the frame settles.
    private fun restored(dark: Boolean) {
        show(CreateUiState(loading = false, games = PreviewGames, restoring = true), dark)
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        val drafts = (0 until 3).map { index ->
            val game = PreviewGames[index % PreviewGames.size]
            CreateUiState.DraftRow(
                id = "d$index", gameId = game.id, brand = game.brand, title = "Restored ${3 - index}",
                meta = "${game.name} · ${game.defaultSize.label}", edited = "edited ${index + 1} h ago", verdict = Verdict.Checking,
            )
        }
        rule.runOnUiThread { state = state.copy(drafts = drafts, draftCount = drafts.size, restoring = false) }
        rule.waitForIdle()
        rule.settleAndCapture("restore", "create-restored", "phone", dark)
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
