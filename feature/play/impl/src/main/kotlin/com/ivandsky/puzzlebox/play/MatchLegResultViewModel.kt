package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.DuelSide
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.ResultHeroFacts
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What a finished duel puzzle's result page shows. */
internal sealed interface MatchLegResultState {

    /** Before the match is first read. */
    data object Loading : MatchLegResultState

    /** The duel can't be shown: [reason] is the match state that says why (never a loaded one). */
    data class Unavailable(val reason: MatchState) : MatchLegResultState

    /**
     * The puzzle [number] of [count] against [opponent] (null while the seat is open): both sides of the
     * card, the leg's [verdict] and [rankOrder] for its caption, the [posting] state of my result (null once
     * Posted), the [hero] facts (null after a give up, which has no hero) and the one [next] action.
     */
    data class Ready(
        val number: Int,
        val count: Int,
        val opponent: String?,
        val you: DuelSide,
        val them: DuelSide,
        val verdict: LegVerdict,
        val rankOrder: RankOrder,
        val posting: ResultPostState?,
        val hero: ResultHeroFacts?,
        val brand: GameBrand,
        val next: LegNext,
    ) : MatchLegResultState
}

/** The button under the card: the next unplayed puzzle, or, with none left, the reveal. */
internal sealed interface LegNext {

    /** "Next puzzle: [game] #[number]" ([number] is null for a shared puzzle): opens [key]. */
    data class Puzzle(val key: MatchLegKey, val game: String, val number: Int?) : LegNext

    data object Reveal : LegNext
}

/**
 * The state holder of a duel puzzle's result page for [key]. Everything on the page comes from
 * [ObserveMatch]: my side from the key (it rides there, so the page renders after process death), the
 * opponent's from the match's view, the posting state from my outbox row. The leg's live play
 * ([PlaySessionStore.peek]) only adds the hero's verdict and previous best while it still holds this solve; after a
 * cold start they are absent. Read-only: it never writes the outbox or the match.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MatchLegResultViewModel(
    private val key: MatchLegResultKey,
    private val store: PlaySessionStore,
    observeMatch: ObserveMatch,
    private val games: GameRegistry,
    private val ui: GamePlayRegistry,
) : ViewModel() {

    val state: StateFlow<MatchLegResultState> = (
        ShareCode.parse(key.code)?.let { code -> observeMatch(code).flatMapLatest(::stateOf) }
            ?: flowOf(MatchLegResultState.Unavailable(MatchState.NotFound))
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MatchLegResultState.Loading)

    private fun stateOf(match: MatchState): Flow<MatchLegResultState> {
        if (match !is MatchState.Loaded) return flowOf(MatchLegResultState.Unavailable(match))
        val ref = match.view.match.legs.getOrNull(key.legIndex)?.ref
            ?: return flowOf(MatchLegResultState.Unavailable(MatchState.NotFound))
        val live = store.peek(PlaySlot.Leg(ref.gameId, key.code, key.legIndex))
        return (live?.record ?: flowOf(null)).map { record -> ready(match.view, ref, record) }
    }

    private fun ready(view: MatchView, ref: LegRef, record: ActivePlay.Record?): MatchLegResultState.Ready {
        val match = view.match
        val order = games.rankOrderOf(ref.gameId)
        val me = if (match.challenger.uid == view.me) match.challenger else match.opponent
        val them = match.opponentOf(view.me)
        val row = view.mine.firstOrNull { it.result.legIndex == key.legIndex }
        // The outbox row is the truth (a give up can race a solve); the key only stands in before it lands.
        val gaveUp = row?.result?.gaveUp ?: key.gaveUp
        val timeMs = row?.result?.timeMs ?: key.timeMs
        val hints = row?.result?.hints ?: key.hintsUsed
        val own = record?.takeIf { it.solve == ActivePlay.Solve(timeMs, hints) }
        return MatchLegResultState.Ready(
            number = key.legIndex + 1,
            count = match.legs.size,
            opponent = them?.name,
            you = DuelSide(
                me?.name ?: YOU,
                me?.photoUrl,
                if (gaveUp) DuelSideState.GaveUp else DuelSideState.Time(timeMs, hints),
            ),
            them = DuelSide(them?.name ?: OPPONENT, them?.photoUrl, theirSide(view)),
            verdict = view.outcome.legs.getOrElse(key.legIndex) { LegVerdict.Pending },
            rankOrder = order,
            posting = row?.state?.takeIf { it != ResultPostState.Posted },
            hero = if (gaveUp) null else heroFacts(ref, order, timeMs, hints, own),
            brand = ui.brandOf(ref.gameId),
            next = nextOf(view),
        )
    }

    /** Never a time before both have finished (A7): a played leg stays sealed until the view reveals it. */
    private fun theirSide(view: MatchView): DuelSideState = when (val seen = view.theirs.getOrNull(key.legIndex)) {
        is LegSeen.Revealed -> seen.result.let { if (it.gaveUp) DuelSideState.GaveUp else DuelSideState.Time(it.timeMs, it.hints) }
        LegSeen.Played -> DuelSideState.Sealed
        LegSeen.NotPlayed, null -> DuelSideState.NotPlayed
    }

    /** The hero page's facts: the key's solve; the verdict and previous best only from a play still holding it. */
    private fun heroFacts(ref: LegRef, order: RankOrder, timeMs: Long, hints: Int, own: ActivePlay.Record?) = ResultHeroFacts(
        timeMs = timeMs,
        hintsUsed = hints,
        verdict = own?.verdict,
        previousBest = own?.previousBest,
        rankOrder = order,
        game = games.displayName(ref.gameId),
        puzzleNumber = (ref as? LegRef.Daily)?.number,
        practice = false,
        replay = false,
        dateLine = (ref as? LegRef.Daily)?.let { resultDateLine(it.day, today = false) } ?: SHARED_PUZZLE,
        doneToday = null,
        total = null,
        insights = null,
        rank = ResultRank.Hidden,
        primary = null,
    )

    /** The first puzzle without a result of mine; played and given-up ones are taken, and so is this one. */
    private fun nextOf(view: MatchView): LegNext {
        val taken = view.mine.mapTo(HashSet()) { it.result.legIndex } + key.legIndex
        val index = view.match.legs.indices.firstOrNull { it !in taken } ?: return LegNext.Reveal
        val ref = view.match.legs[index].ref
        return LegNext.Puzzle(
            key = MatchLegKey(key.code, index, ref.gameId.value),
            game = games.displayName(ref.gameId),
            number = (ref as? LegRef.Daily)?.number,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val YOU = "You"
        const val OPPONENT = "Opponent"
        const val SHARED_PUZZLE = "Shared puzzle"
    }
}
