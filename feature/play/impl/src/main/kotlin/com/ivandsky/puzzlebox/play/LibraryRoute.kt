package com.ivandsky.puzzlebox.play

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialHost
import com.ivandsky.puzzlebox.model.GameId
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The Play tab's root (A25–A29, N2): the games library. [PlayLayout.TwoPane] shows the
 * list beside the selected game's page, a game always selected; [PlayLayout.SinglePane]
 * shows the list and pushes a game's page over it, back returning to the list. The day
 * card's action is [onPlay] (the entry pushes the game's key, so back from the board
 * returns here, N3). A row whose today can't load also retries; each start prefetches
 * today's uncached puzzles ([LibraryViewModel.syncToday], A12). "Open the lesson" runs
 * the game's tutorial over the library.
 */
@Composable
internal fun LibraryRoute(
    layout: PlayLayout,
    onPlay: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = koinViewModel()
    val registry: GamePlayRegistry = koinInject()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lesson by viewModel.lesson.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.syncToday()
        onStopOrDispose { }
    }
    Box(modifier.fillMaxSize().testTag("route.library")) {
        LibraryScreen(
            state = state,
            layout = layout,
            hasLesson = { registry[it]?.tutorial?.isNotEmpty() == true },
            onSelect = viewModel::select,
            onPlay = onPlay,
            onRetry = viewModel::syncToday,
            onOpenLesson = viewModel::openLesson,
        )
        val open = lesson
        val ui = open?.let { registry[it.game] }
        if (open != null && ui != null) {
            BackHandler { viewModel.closeLesson() }
            TutorialHost(
                ui = ui,
                session = open.session,
                onFinished = viewModel::closeLesson,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            )
        }
    }
}

/** The library for [state]: list and page side by side, or the phone's list → page push. */
@Composable
internal fun LibraryScreen(
    state: LibraryUiState,
    layout: PlayLayout,
    hasLesson: (GameId) -> Boolean,
    onSelect: (GameId) -> Unit,
    onPlay: (GameId) -> Unit,
    onRetry: () -> Unit,
    onOpenLesson: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listScroll = rememberScrollState()
    val onRow: (GameId) -> Unit = { game ->
        onSelect(game)
        if (state.rows.firstOrNull { it.card.game == game }?.card?.status == TodayStatus.Offline) {
            onRetry()
        }
    }
    @Composable
    fun Page(row: LibraryRowModel, onBack: (() -> Unit)?) {
        val game = row.card.game
        GamePage(
            row = row,
            layout = layout,
            dayCard = {
                ArchiveDayCard(
                    row = row,
                    today = row.progress?.range?.endInclusive,
                    onPlay = { onPlay(game) },
                    onRetry = onRetry,
                )
            },
            calendar = {},
            onBack = onBack,
            onOpenLesson = { onOpenLesson(game) },
            offline = state.offline,
            hasLesson = hasLesson(game),
        )
    }
    val selected = state.rows.firstOrNull { it.card.game == state.selected } ?: state.rows.firstOrNull()
    if (layout == PlayLayout.TwoPane) {
        Row(modifier.fillMaxSize().padding(start = Spacing.xl, top = Spacing.lg, end = Spacing.xxl, bottom = Spacing.xl)) {
            LibraryList(state, layout, listScroll, onRow, Modifier.width(400.dp))
            Spacer(Modifier.width(Spacing.xl))
            if (selected != null) {
                key(selected.card.game) {
                    Box(Modifier.fillMaxWidth()) { Page(selected, onBack = null) }
                }
            }
        }
    } else {
        var pageOpen by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = pageOpen) { pageOpen = false }
        val reduced = rememberReducedMotion()
        AnimatedContent(
            targetState = pageOpen && selected != null,
            transitionSpec = { libraryPush(reduced) },
            label = "library-push",
            modifier = modifier.fillMaxSize(),
        ) { open ->
            if (open && selected != null) {
                Page(selected, onBack = { pageOpen = false })
            } else {
                LibraryList(state, layout, listScroll, { game ->
                    onRow(game)
                    pageOpen = true
                })
            }
        }
    }
}
