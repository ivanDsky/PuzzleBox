package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.PuzzleCodeCheck
import com.ivandsky.puzzlebox.model.ShareCode
import kotlin.random.Random

/**
 * Starts a duel ("Start duel", "Rematch"): draws its puzzles once, picks a [random] code that is neither a shared
 * puzzle's nor a match's, writes the match and remembers it as mine, so it lists at once. A rematch invites the
 * other player of [Match] `rematchOf` and links its code. A taken code is replaced a bounded number of times.
 * The challenger's name is trimmed and cut to the rules' bound, a blank one becoming the anonymous player label.
 */
class CreateMatch(
    private val repo: MatchRepository,
    private val local: MatchLocalRepository,
    private val draw: ComputeMatchLegs,
    private val identity: IdentityService,
    private val random: Random,
) {
    suspend operator fun invoke(settings: DuelSettings, rematchOf: Match? = null): CreateOutcome {
        val me = identity.current.value ?: return CreateOutcome.SignedOut
        val legs = draw(
            DrawRequest(
                count = settings.count,
                games = settings.games,
                todayOnly = settings.todayOnly,
                includeFriends = settings.includeFriends,
                pastedCodes = settings.pastedCodes,
                me = me.uid,
            ),
        )
        if (legs.isEmpty()) return CreateOutcome.NoPuzzles
        val challenger = Player(me.uid, playerName(me.displayName), me.photoUrl)
        val rival = rematchOf?.opponentOf(me.uid)
        repeat(MAX_CODE_ATTEMPTS) {
            val code = ShareCode.random(random)
            when (val check = repo.puzzleCodeTaken(code)) {
                is PuzzleCodeCheck.Unavailable ->
                    return if (check.offline) CreateOutcome.Offline else CreateOutcome.Failed(check.message)
                PuzzleCodeCheck.Taken -> return@repeat
                PuzzleCodeCheck.Free -> Unit
            }
            val draft = MatchDraft(
                code = code,
                deadlineHours = settings.deadlineHours,
                challenger = challenger,
                invitedUid = rival?.uid,
                legs = legs,
                includeFriends = settings.includeFriends,
                rematchOf = rematchOf?.code,
            )
            when (val result = repo.create(draft)) {
                is CreateResult.Created -> {
                    local.remember(result.code, LocalRole.Participant, opponentName = rival?.name)
                    return CreateOutcome.Created(result.code, legs)
                }
                CreateResult.CodeTaken -> Unit
                CreateResult.DailyCapReached -> return CreateOutcome.DailyCapReached
                CreateResult.Offline -> return CreateOutcome.Offline
                CreateResult.SignedOut -> return CreateOutcome.SignedOut
                is CreateResult.Rejected -> return CreateOutcome.Failed(result.message)
            }
        }
        return CreateOutcome.Failed(null)
    }

    /** As the leaderboard writes it: trimmed, the anonymous label when blank, at most the rules' 64 characters. */
    private fun playerName(displayName: String): String = displayName.trim().ifEmpty { ANONYMOUS_NAME }.take(MAX_NAME_LENGTH)

    private companion object {
        /** The first code plus as many replacements as `CreateSharedPuzzle` allows (its `MAX_REGENERATIONS`). */
        const val MAX_CODE_ATTEMPTS = 4
        const val MAX_NAME_LENGTH = 64
        const val ANONYMOUS_NAME = "Player"
    }
}
