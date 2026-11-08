package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.domain.TodayLeaderboard

/** Rank = list position + 1; isSelf = entry uid == the signed-in uid. [note] applies only to Loaded. */
fun TodayLeaderboard.toDailyBoardState(note: DailyBoardNote? = null): DailyBoardState = when (this) {
    TodayLeaderboard.SignedOut -> DailyBoardState.SignedOut
    is TodayLeaderboard.SignedIn -> when (val board = board) {
        TodayLeaderboard.Board.Loading -> DailyBoardState.Loading
        TodayLeaderboard.Board.Unavailable -> DailyBoardState.Unavailable
        is TodayLeaderboard.Board.Loaded -> DailyBoardState.Ranked(
            rows = board.snapshot.entries.mapIndexed { index, entry ->
                DailyBoardRowModel(
                    rank = index + 1,
                    name = entry.displayName,
                    timeMs = entry.timeMs,
                    hints = entry.hints,
                    isSelf = entry.uid == identity.uid,
                )
            },
            note = note,
        )
    }
}

/** The line for a post whose row has not landed; null once Posted, when there is nothing left to say. */
fun SubmitStatus.toBoardNote(): DailyBoardNote? = when (this) {
    SubmitStatus.Posting -> DailyBoardNote.Posting
    SubmitStatus.Deferred -> DailyBoardNote.Deferred
    SubmitStatus.Rejected -> DailyBoardNote.Rejected
    SubmitStatus.Posted -> null
}
