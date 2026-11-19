package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.ranks.RankedBoard
import com.ivandsky.puzzlebox.domain.ranks.rankBoard
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.play.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import java.time.Instant
import java.time.LocalDate

internal val TODAY: LocalDate = LocalDate.of(2026, 11, 16)
internal val ROLLOVER: Instant = Instant.parse("2026-11-17T08:00:00Z")

/** Facts for a counted solve of alpha, signed in as "me", with nothing left to offer but the countdown. */
internal fun nextThingFacts(
    playAgain: Boolean = false,
    practice: Boolean = false,
    todaySet: List<TodayEntry> = listOf(TodayEntry(alpha, "Alpha", TodayStatus.Solved(40_000))),
    history: List<GameHistoryDay> = emptyList(),
    friendPuzzles: List<FriendPuzzleRow> = emptyList(),
    viewerUid: String? = "me",
    friendBoard: suspend () -> RankedBoard? = { null },
    friendTier: suspend (uid: String) -> VisibilityTier? = { null },
    communityPuzzles: suspend () -> List<NextThing.CommunityPuzzle> = { emptyList() },
) = NextThingFacts(
    game = alpha,
    today = TODAY,
    playAgain = playAgain,
    practice = practice,
    todaySet = todaySet,
    history = history,
    friendPuzzles = friendPuzzles,
    nextRollover = ROLLOVER,
    viewerUid = viewerUid,
    friendBoard = friendBoard,
    friendTier = friendTier,
    communityPuzzles = communityPuzzles,
)

/** Today's alpha Friends board from uid to time pairs; a display name is the uid capitalised. */
internal fun friendBoardOf(vararg times: Pair<String, Long>): RankedBoard {
    val ref = LeaderboardRef(alpha.value, TODAY)
    val entries = times.map { (uid, timeMs) ->
        LeaderboardEntry(uid, uid.replaceFirstChar(Char::uppercase), "${alpha.value}/$TODAY/1", timeMs, 0, 1L)
    }
    return rankBoard(ref, entries, RankOrder.TimeThenHints, entries.size)
}
