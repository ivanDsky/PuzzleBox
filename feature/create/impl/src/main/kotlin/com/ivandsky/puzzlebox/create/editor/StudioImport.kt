package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.importtext.ImportTextState
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.ImportDraftFromText
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The Studio's Import from text (M5-07), built from what the Studio already holds, the way
 * `DomainModule` binds [ImportDraftFromText]: the sheet's [text] holder, whether the overflow
 * offers it ([enabled]: [ReleaseFlag.TextImport] is on, live) and the id of each draft it
 * imports on [imported], once each, for the route to open in the Studio's place.
 */
internal class StudioImport(
    games: GameRegistry,
    drafts: DraftRepository,
    dispatchers: DispatcherProvider,
    clock: () -> Instant,
    releaseFlags: ReleaseFlags,
    scope: CoroutineScope,
) {
    private val ids = Channel<String>(Channel.BUFFERED)

    val imported: Flow<String> = ids.receiveAsFlow()

    val text = ImportTextState(
        importDraft = ImportDraftFromText(
            games,
            CreateDraft(games, drafts, newId = { UUID.randomUUID().toString() }, clock = clock),
            drafts,
            dispatchers,
        ),
        scope = scope,
        gameName = { games.displayName(GameId(it)) },
        onImported = { ids.trySend(it) },
    )

    val enabled: StateFlow<Boolean> = releaseFlags.enabled.map { ReleaseFlag.TextImport in it }.stateIn(
        scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReleaseFlag.TextImport in releaseFlags.enabled.value,
    )
}
