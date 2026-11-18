package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The titles of custom duel legs, for an invite that lists them (spec A8, A19). A code's title is the saved row's (opened
 * from a friend, or published by this player), else the public puzzle's when it can be read now and was not taken down.
 * A code with neither has no entry: the caller shows its code.
 *
 * Emits the empty map at once, then the map again each time a title is learned, so a saved title shows without waiting
 * for a read. Ends when every code is settled; the last map is the whole answer. Reads run side by side.
 */
class ObserveLegTitles(
    private val received: ReceivedPuzzleRepository,
    private val published: PublishedPuzzleRepository,
    private val shared: SharedPuzzleRepository,
) {
    operator fun invoke(codes: Collection<ShareCode>): Flow<Map<ShareCode, String>> = channelFlow {
        val known = mutableMapOf<ShareCode, String>()
        val lock = Mutex()
        send(emptyMap())
        for (code in codes.toSet()) {
            launch {
                val title = titleOf(code) ?: return@launch
                lock.withLock {
                    known[code] = title
                    send(known.toMap())
                }
            }
        }
    }

    private suspend fun titleOf(code: ShareCode): String? =
        received.get(code)?.title
            ?: published.observe(code).first()?.title
            ?: (shared.fetch(code) as? SharedFetchResult.Found)?.doc?.takeUnless { it.unpublished }?.title
}
