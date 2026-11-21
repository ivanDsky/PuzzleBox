package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.visibility.TierLookup
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.isStreakDay
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.ranks.BoardLoad
import com.ivandsky.puzzlebox.domain.ranks.LoadFriendsPastPage
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.domain.ranks.RankedBoard
import com.ivandsky.puzzlebox.domain.ranks.rankBoard
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.domain.moments.SolveFacts
import com.ivandsky.puzzlebox.domain.moments.detectMoments
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Everything Result needs to celebrate one solve and to offer the next thing (L1, L3, L4), gathered from the
 * phone and the network as one flow. The caller gives the solve; today's done count and streak, the history, the
 * received friend puzzles, the Friends day board and the friends' tiers are read here.
 *
 * The flow emits in the order the sources land. The first emission is the moments the phone alone settles; the
 * Friends day board, read once, adds the top-three and passed-a-friend moments when it lands, and the
 * [NextThingChain]'s answer arrives beside it. The friend to challenge (L4) is asked for apart from the chain, so
 * the Challenge row can stand beside a main button that offers something else, and arrives when the board and that
 * friend's tier have. The board read and each provider are bounded by [timeout], so neither the celebration nor
 * the button waits on the network longer than that; a board that does not answer in time adds no moment and no
 * Challenge. The flow ends when all three have settled.
 *
 * A play again or a practice puzzle earns no moments and gets no next thing: one empty emission, and nothing is
 * read. Signed out, only the local moments and the local rungs come.
 *
 * The Friends board is the viewer and their friends on the day's leaderboard whatever the Friends/Global setting
 * says, because Challenge needs it (L4); it is never widened to the Global board. It is read once from the server;
 * a read that cannot answer is no board. The solve's own entry may not have reached the board yet, so the player is
 * placed on it at the solve's time, and "before" is the board without them. A friend's tier is read once, only for
 * the friend Challenge would offer, whether the chain's rung, the row or both ask, and a read that fails offers
 * nothing (ADR-0031 §15).
 *
 * With [friendsPast] (backlog #1779) a friend who placed past the board's read page is read by
 * id and is on the Friends board, as on the hub's, under the same [timeout]: a board that is slow for it is no board.
 */
class ObserveResultNextThing(
    private val registry: GameRegistry,
    private val identity: IdentityService,
    private val history: ObserveGameHistory,
    private val todaySet: ObserveTodaySet,
    private val friendPuzzles: ObserveFriendPuzzles,
    private val friendUids: ObserveFriendUids,
    private val leaderboard: LeaderboardRepository,
    private val visibility: VisibilityRepository,
    private val computeStreak: ComputeStreak,
    private val puzzleDay: GetPuzzleDay,
    private val nextRollover: GetNextRollover,
    private val communityPuzzles: ListCommunityPuzzles,
    private val timeout: Duration = 2.seconds,
    private val failures: FailureLog = LogcatFailureLog,
    private val friendsPast: LoadFriendsPastPage? = null,
) {
    private val chain = NextThingChain(timeout = timeout, failures = failures)

    /** The Challenge row's own question: the challenge rung alone, under the same time limit and failure rule. */
    private val challengeRow = NextThingChain(listOf(ChallengeFriendNextThingProvider), timeout, failures)

    /**
     * [solve] is the player's time and hints for [game]'s puzzle of [day]. [playAgain] replays a day that is not
     * today; [practice] is a puzzle with no edition number; [newBest] is the record verdict's new-best flag.
     */
    operator fun invoke(
        game: GameId,
        day: LocalDate,
        solve: BestScore,
        playAgain: Boolean,
        practice: Boolean,
        newBest: Boolean,
    ): Flow<ResultNextThing> {
        if (playAgain || practice) return flowOf(ResultNextThing())
        return channelFlow {
            val viewer = identity.current.value
            // Started first so the network read runs while the phone is read.
            val friendDay = async { viewer?.let { readFriendDay(it, game, day, solve) } }
            val days = history().first()
            val set = todaySet().first()
            val local = localFacts(game, day, solve, newBest, days, set)

            val gate = Mutex()
            var current = ResultNextThing(detectMoments(local))
            suspend fun update(change: (ResultNextThing) -> ResultNextThing) = gate.withLock {
                val next = change(current)
                if (next != current) {
                    current = next
                    send(next)
                }
            }
            send(current)

            launch {
                friendDay.await()?.let { board -> update { it.copy(moments = detectMoments(local.copy(friends = board))) } }
            }
            launch {
                val facts = NextThingFacts(
                    game = game,
                    today = puzzleDay(),
                    playAgain = false,
                    practice = false,
                    todaySet = set,
                    history = days,
                    friendPuzzles = friendPuzzles().first(),
                    nextRollover = nextRollover(),
                    viewerUid = viewer?.uid,
                    friendBoard = { friendDay.await()?.after },
                    friendTier = tierReadOnce(),
                    communityPuzzles = ::communityOffers,
                )
                launch {
                    (challengeRow.offer(facts) as? NextThing.ChallengeFriend)?.let { friend -> update { it.copy(challenge = friend) } }
                }
                chain.offer(facts)?.let { next -> update { it.copy(nextThing = next) } }
            }
        }
    }

    /**
     * The community list as offers, newest first, read when the chain asks. A list that is offline or refused offers
     * nothing. A puzzle of a game this build does not register is left out: it could not be opened.
     */
    private suspend fun communityOffers(): List<NextThing.CommunityPuzzle> {
        val listed = communityPuzzles() as? CommunityListResult.Listed ?: return emptyList()
        return listed.puzzles.filter { registry[GameId(it.gameId)] != null }
            .map { NextThing.CommunityPuzzle(it.code, GameId(it.gameId), it.authorName) }
    }

    /** [tierOf] for one solve: each friend's tier is read once however many ask, and the others wait for that read. */
    private fun tierReadOnce(): suspend (String) -> VisibilityTier? {
        val gate = Mutex()
        val read = mutableMapOf<String, VisibilityTier?>()
        return { uid -> gate.withLock { if (uid in read) read[uid] else tierOf(uid).also { read[uid] = it } } }
    }

    /** What the phone knows of the solve. The solve's own record may not have landed, so its history row is left out and counted here. */
    private fun localFacts(
        game: GameId,
        day: LocalDate,
        solve: BestScore,
        newBest: Boolean,
        days: List<GameHistoryDay>,
        set: List<TodayEntry>,
    ): SolveFacts {
        val rest = days.filterNot { it.gameId == game && it.day == day }
        val streakDays = rest.filter { it.isStreakDay() }.mapTo(mutableSetOf()) { it.day }
        return SolveFacts(
            timeMs = solve.timeMs,
            hints = solve.hints,
            playAgain = false,
            practice = false,
            newBest = newBest,
            bestHintFreeMs = rest.filter { it.gameId == game }.mapNotNull { it.record }.filter { it.hints == 0 }.minOfOrNull { it.time },
            doneToday = set.count { it.game != game && it.status is TodayStatus.Solved } + 1,
            totalToday = set.size,
            dayTotal = dayTotal(game, day, solve, days, set),
            streakBefore = computeStreak(streakDays).current,
            streak = computeStreak(streakDays + day).current,
        )
    }

    /** The set's summed time and hints: this solve, plus each other solved game's time from the set and its hints from that day's record. */
    private fun dayTotal(game: GameId, day: LocalDate, solve: BestScore, days: List<GameHistoryDay>, set: List<TodayEntry>): Moment.FullSetDone.DayTotal {
        val others = set.filter { it.game != game }.mapNotNull { entry ->
            (entry.status as? TodayStatus.Solved)?.let { solved ->
                solved.timeMs to (days.firstOrNull { it.gameId == entry.game && it.day == day }?.record?.hints ?: 0)
            }
        }
        return Moment.FullSetDone.DayTotal(solve.timeMs + others.sumOf { it.first }, solve.hints + others.sumOf { it.second })
    }

    /**
     * The Friends board before and after this solve, or null when it cannot be read within [timeout] (signed out
     * is no viewer, so no call). A read that throws is logged and is no board.
     */
    private suspend fun readFriendDay(viewer: Identity, game: GameId, day: LocalDate, solve: BestScore): SolveFacts.FriendDay? =
        withTimeoutOrNull(timeout) {
            try {
                val ref = LeaderboardRef(game.value, day)
                val order = registry.rankOrderOf(game)
                val page = leaderboard.fetch(ref, order) ?: return@withTimeoutOrNull null
                val players = friendUids().first() + viewer.uid
                val past = friendsPast?.invoke(LoadFriendsPastPage.Memo(), RankWindowBoards.Boards(listOf(BoardLoad.Loaded(ref, order, page, final = false))), players, viewer.uid)?.get(ref).orEmpty()
                val onPage = page.entries.mapTo(HashSet()) { it.uid }
                val friends = page.entries.filter { it.uid in players && it.uid != viewer.uid } + past.filter { it.uid in players && it.uid != viewer.uid && onPage.add(it.uid) }
                val onBoard = page.entries.firstOrNull { it.uid == viewer.uid }
                val mine = onBoard ?: LeaderboardEntry(viewer.uid, viewer.displayName, "${game.value}/$day", solve.timeMs, solve.hints, completedAtMs = 0)
                val after = friends + mine
                // An entry that is not this solve (the first solve of the day stands) means this solve moved nobody.
                val thisSolve = onBoard == null || (onBoard.timeMs == solve.timeMs && onBoard.hints == solve.hints)
                val before = if (thisSolve) friends else after
                fun ranked(entries: List<LeaderboardEntry>): RankedBoard = rankBoard(ref, entries, order, entries.size)
                SolveFacts.FriendDay(viewer.uid, ranked(before), ranked(after))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.warn(TAG, "friends board read failed", e)
                null
            }
        }

    /** One read of [uid]'s tier; a failed read is null, so nothing is offered (ADR-0031 §15). */
    private suspend fun tierOf(uid: String) = (visibility.tierOf(uid) as? TierLookup.Found)?.tier

    private companion object {
        const val TAG = "ObserveResultNextThing"
    }
}
