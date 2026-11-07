package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.domain.PublishOutcome
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Studio's share ticket, started in [scope]: the published code from [publish], the
 * [title], the author name, the author's test time from [passed] and the open board's game
 * name and hue, struck once the stored row in [publishedPuzzles] is unpublished. Fresh only
 * when the code was published here rather than reopened. Null until a publish lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun studioTicket(
    publish: StateFlow<PublishSession.State>,
    title: StateFlow<String>,
    passed: StateFlow<TestSolve?>,
    editor: StateFlow<EditorUiState>,
    publishedPuzzles: PublishedPuzzleRepository,
    games: GameRegistry,
    plays: GamePlayRegistry,
    scope: CoroutineScope,
): StateFlow<ShareTicket?> {
    val unpublished: Flow<Boolean> = publish
        .map { (it.outcome as? PublishOutcome.Published)?.code }
        .distinctUntilChanged()
        .flatMapLatest { code -> code?.let { publishedPuzzles.observe(it).map { row -> row?.unpublished == true } } ?: flowOf(false) }
    return combine(publish, title, passed, editor, unpublished) { session, named, test, open, takenDown ->
        val code = (session.outcome as? PublishOutcome.Published)?.code ?: return@combine null
        val gameId = (open as? EditorUiState.Ready)?.controller?.draft?.value?.gameId ?: return@combine null
        ShareTicket(
            code = code,
            title = named,
            author = session.details.authorName.trim(),
            authorTimeMs = test?.timeMs,
            game = games.displayName(gameId),
            accent = plays.brandOf(gameId).accent,
            unpublished = takenDown,
            fresh = !session.reopened,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)
}
