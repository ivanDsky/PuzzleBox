package com.ivandsky.puzzlebox.compete.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode

internal suspend fun ReceivedPuzzleRepository.legOf(ref: PuzzleRef): MatchLeg = when (val key = ref.key) {
    is PuzzleKey.Daily -> MatchLeg(LegRef.Daily(ref.gameId, key.date, checkNotNull(key.puzzleNumber)), authorUid = null)
    is PuzzleKey.Custom -> {
        val code = checkNotNull(ShareCode.parse(key.puzzleId))
        MatchLeg(LegRef.Custom(ref.gameId, code), authorUid = get(code)?.authorUid)
    }
}
