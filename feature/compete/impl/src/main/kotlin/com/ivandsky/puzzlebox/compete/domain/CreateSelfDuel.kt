package com.ivandsky.puzzlebox.compete.domain

import com.ivandsky.puzzlebox.compete.domain.lineup.ResolveSelfLineup
import com.ivandsky.puzzlebox.compete.domain.lineup.SelfDraw
import com.ivandsky.puzzlebox.compete.domain.lineup.legOf
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.buildSelfMatch
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Starts a duel against your own first solves: draws up to [SelfDuelSettings.rounds] puzzles at random from the
 * [ComputeSolvedPool] list, freezes each puzzle's record as past you's result and saves the duel on this device.
 * It needs no account and has no daily cap. The code is a [ShareCode.random] that neither a self duel nor a
 * friend duel on this device already holds. An empty pool answers [CreateOutcome.NoPuzzles]. With a
 * [SelfDuelSettings.lineup] the legs are the lineup's instead ([ResolveSelfLineup]); a row it can't fill answers
 * [CreateOutcome.EntryUnavailable] or [CreateOutcome.EntryUndrawable] and nothing is saved.
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
    private val resolveLineup = ResolveSelfLineup(pool, completions, received, random)

    suspend operator fun invoke(settings: SelfDuelSettings): CreateOutcome {
        val picked = when (val lineup = settings.lineup) {
            null -> drawAtRandom(settings)
            else -> when (val draw = resolveLineup(lineup, settings.includeFriends)) {
                is SelfDraw.Drawn -> draw.picked
                is SelfDraw.Unavailable -> return CreateOutcome.EntryUnavailable(draw.position)
                is SelfDraw.Undrawable -> return CreateOutcome.EntryUndrawable(draw.position, draw.gameId)
            }
        }
        if (picked.isEmpty()) return CreateOutcome.NoPuzzles
        val legs = picked.map { it.first }
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

    private suspend fun drawAtRandom(settings: SelfDuelSettings): List<Pair<MatchLeg, CompletionRecord>> {
        val drawn = pool(settings.games, settings.includeFriends).shuffled(random).take(maxOf(settings.rounds, 0))
        if (drawn.isEmpty()) return emptyList()
        val records = completions.observeRecords().first().filter { it.isRecord }.associateBy { it.puzzleRef.identity() }
        return drawn.mapNotNull { ref -> records[ref.identity()]?.let { received.legOf(ref) to it } }
    }

    private suspend fun freshCode(): ShareCode {
        val taken = local.observeAll().first().mapTo(HashSet()) { it.code }
        while (true) {
            val code = ShareCode.random(random)
            if (code !in taken && !self.holds(code)) return code
        }
    }

    private fun PuzzleRef.identity() = gameId to key
}
