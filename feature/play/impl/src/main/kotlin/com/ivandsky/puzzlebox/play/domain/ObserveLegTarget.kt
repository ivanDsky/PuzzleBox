package com.ivandsky.puzzlebox.play.domain

import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.data.match.isSelf
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.model.ShareCode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Past you's time for an open leg of a self duel: the target the leg's timer counts down to. Null for a friend duel's
 * leg (the self store does not hold it), for a leg whose result of mine is already posted, and while the "Show time
 * left when you duel yourself" setting is off. It is the only read of past you's score before the reveal, and it
 * reads the self store directly, so the sealed match view never carries it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveLegTarget(
    private val self: SelfMatchRepository,
    private val preferences: UserPreferencesRepository,
) {

    operator fun invoke(code: ShareCode, legIndex: Int): Flow<Duration?> =
        combine(preferences.showSelfDuelTimeLeft, self.observe(code).map { open(it, legIndex) }.distinctUntilChanged()) { on, open ->
            on && open
        }.distinctUntilChanged().flatMapLatest { show ->
            if (show) flow<Duration?> { emit(pastTime(code, legIndex)) } else flowOf(null)
        }.distinctUntilChanged()

    /** True for a self duel whose leg [legIndex] I have not posted. */
    private fun open(fetch: MatchFetch, legIndex: Int): Boolean {
        val match = (fetch as? MatchFetch.Found)?.match?.takeIf { it.isSelf } ?: return false
        return legIndex in match.legs.indices && legIndex !in match.progress[SelfSeats.ME].orEmpty()
    }

    private suspend fun pastTime(code: ShareCode, legIndex: Int): Duration? =
        (self.readResult(code, legIndex, SelfSeats.PAST) as? ResultRead.Found)?.result?.timeMs?.milliseconds
}
