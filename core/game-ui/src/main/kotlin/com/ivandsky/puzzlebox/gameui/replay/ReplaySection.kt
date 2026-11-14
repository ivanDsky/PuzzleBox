package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.ResultColumnMaxWidth
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.model.GameId

/**
 * The replay section of a result-like screen. The one place that reads ui.replayBoard:
 * Hidden, or a game with no replay board, draws nothing. Ready draws the Watch replay row;
 * [onOpen] opens the replay page, where the player lives. Unavailable draws the same row, disabled, with the reason as its second line.
 * A [label] names which solve the row is, when a screen shows more than one.
 */
@Composable
fun ReplaySection(state: ReplaySectionState, ui: GamePlayUi, onOpen: () -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    if (ui.replayBoard == null) return
    val capped = modifier.widthIn(max = ResultColumnMaxWidth)
    when (state) {
        ReplaySectionState.Hidden -> Unit
        is ReplaySectionState.Ready ->
            WatchReplayRow(state.timeline.moveTimesMs.size, state.timeline.endMs, onOpen, capped.testTag(ReplayTags.WATCH_ROW), label)
        is ReplaySectionState.Unavailable -> UnavailableWatchReplayRow(state.reason, capped.testTag(ReplayTags.UNAVAILABLE), label)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplaySectionPreview() {
    PuzzleBoxTheme { ReplaySection(ReplaySectionState.Hidden, PreviewGame, onOpen = {}) }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplaySectionUnavailableImportedPreview() {
    PuzzleBoxTheme { ReplaySection(ReplaySectionState.Unavailable(ReplayUnavailable.ImportedFromLinkedIn), PreviewGame, onOpen = {}) }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplaySectionUnavailableNotRecordedPreview() {
    PuzzleBoxTheme { ReplaySection(ReplaySectionState.Unavailable(ReplayUnavailable.NotRecorded), PreviewGame, onOpen = {}) }
}

private object PreviewGame : GamePlayUi {
    override val gameId = GameId("preview")
    override val tutorial: List<TutorialStep> = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController = error("preview only")

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}
