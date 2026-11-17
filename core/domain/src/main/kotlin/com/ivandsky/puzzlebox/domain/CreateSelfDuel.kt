package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Starts a duel against your own first solves: draws up to [SelfDuelSettings.rounds] puzzles at random from the
 * [ComputeSolvedPool] list, freezes each puzzle's record as past you's result and saves the duel on this device.
 * It needs no account and has no daily cap. The code is a [ShareCode.random] that neither a self duel nor a
 * friend duel on this device already holds. An empty pool answers [CreateOutcome.NoPuzzles].
 */
class CreateSelfDuel(
    private val pool: ComputeSolvedPool,
    private val completions: CompletionRepository,
    private val received: ReceivedPuzzleRepository,
    private val self: SelfMatchRepository,
    private val local: MatchLocalRepository,
    private val clock: () -> Instant,
    private val random: Random,
) {
    suspend operator fun invoke(settings: SelfDuelSettings): CreateOutcome {
        val drawn = pool(settings.games, settings.includeFriends).shuffled(random).take(maxOf(settings.rounds, 0))
        if (drawn.isEmpty()) return CreateOutcome.NoPuzzles
        val records = completions.observeRecords().first().filter { it.isRecord }.associateBy { it.puzzleRef.identity() }
        val picked = drawn.mapNotNull { ref -> records[ref.identity()]?.let { ref to it } }
        if (picked.isEmpty()) return CreateOutcome.NoPuzzles
        val legs = picked.map { (ref, _) -> leg(ref) }
        val draft = buildSelfMatch(
            code = freshCode(),
            createdAt = clock(),
            includeFriends = settings.includeFriends,
            legs = legs,
            records = picked.map { it.second },
        )
        self.save(draft.match, draft.past)
        return CreateOutcome.Created(draft.match.code, legs)
    }

    private suspend fun freshCode(): ShareCode {
        val taken = local.observeAll().first().mapTo(HashSet()) { it.code }
        while (true) {
            val code = ShareCode.random(random)
            if (code !in taken && !self.holds(code)) return code
        }
    }

    private suspend fun leg(ref: PuzzleRef): MatchLeg = when (val key = ref.key) {
        is PuzzleKey.Daily -> MatchLeg(LegRef.Daily(ref.gameId, key.date, checkNotNull(key.puzzleNumber)), authorUid = null)
        is PuzzleKey.Custom -> {
            val code = checkNotNull(ShareCode.parse(key.puzzleId))
            MatchLeg(LegRef.Custom(ref.gameId, code), authorUid = received.get(code)?.authorUid)
        }
    }

    private fun PuzzleRef.identity() = gameId to key
}
