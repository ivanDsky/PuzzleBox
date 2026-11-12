package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ReplayKey
import java.time.LocalDate

/** Where the solve this key opens came from, for the replay pages' context line ("#7 · Today · Wed 4 Nov", "Duel"). */
internal fun ReplayKey.origin(calendar: GameCalendar): String = when {
    code != null -> "Duel"
    puzzleId != null -> "Friend's puzzle"
    else -> {
        val date = day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        listOfNotNull(
            puzzleNumber?.let { "#$it" },
            date?.let { resultDateLine(it, today = calendar.today(GameId(gameId)) == it) },
        ).joinToString(" · ")
    }
}
