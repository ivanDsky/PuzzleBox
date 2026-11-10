package com.ivandsky.puzzlebox.testing.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory [LinkedInLink] with the real link's state transitions and nothing else: it
 * holds no cookies and no database, so a disconnect here only resets the state.
 */
class FakeLinkedInLink(initial: LinkState = LinkState.NotConnected) : LinkedInLink {

    private val current = MutableStateFlow(initial)
    private val recorded = CopyOnWriteArrayList<Pair<String, Long>>()

    override val state: Flow<LinkState> = current

    /** Every connect so far as `(profileId, consentAtMs)`, oldest first. */
    val connects: List<Pair<String, Long>> get() = recorded.toList()

    override suspend fun connect(profileId: String, consentAtMs: Long) {
        recorded += profileId to consentAtMs
        current.value = LinkState.Connected(profileId)
    }

    /** With no profile there is nothing to pause, as in the real link. */
    override suspend fun pause(kind: LinkedInRead.Rejected.Kind) {
        current.update { state ->
            when (state) {
                LinkState.NotConnected -> state
                is LinkState.Connected -> LinkState.Paused(state.profileId, kind)
                is LinkState.Paused -> LinkState.Paused(state.profileId, kind)
            }
        }
    }

    override suspend fun resume() {
        current.update { state -> if (state is LinkState.Paused) LinkState.Connected(state.profileId) else state }
    }

    override suspend fun disconnect() {
        current.value = LinkState.NotConnected
    }
}
