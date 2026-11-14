package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
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
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The Studio's Import from text, built from what the Studio already holds, the way
 * `DomainModule` binds [ImportDraftFromText]: the sheet's [text] holder
 * and the id of each draft it imports on [imported], once each, for the route to open in the
 * Studio's place.
 */
internal class StudioImport(
    games: GameRegistry,
    drafts: DraftRepository,
    dispatchers: DispatcherProvider,
    clock: () -> Instant,
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
}
