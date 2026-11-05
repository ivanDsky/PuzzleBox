package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.KeptSessionRepository
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Today's set (spec A14): every game in [games], registry order, with its status on its
 * own cadence day. Solved = a completion record with `ref.gameId == game && ref.date ==
 * today` (the identity [ObserveGameHistory] uses; no puzzleNumber match) and beats a kept
 * session. InProgress = a session kept for today. Everything else, a stale kept session
 * included, is NotStarted.
 *
 * [today] is each game's cadence day, read on every emission so a day rollover is picked
 * up on the next change; it is never the device date.
 */
class ObserveTodaySet(
    private val games: GameRegistry,
    private val completions: CompletionRepository,
    private val kept: KeptSessionRepository,
    private val today: (GameDefinition) -> LocalDate = { it.cadence.today() },
) {

    operator fun invoke(): Flow<List<TodayEntry>> =
        combine(completions.observeRecords(), kept.kept) { records, sessions ->
            games.all.map { plugin ->
                val definition = plugin.definition
                val day = today(definition)
                val solved = records.firstOrNull {
                    it.puzzleRef.gameId == definition.id && it.puzzleRef.date == day
                }
                val session = sessions[definition.id]?.takeIf { it.day == day }
                val status = when {
                    solved != null -> TodayStatus.Solved(solved.time)
                    session != null -> TodayStatus.InProgress(session.elapsedMs)
                    else -> TodayStatus.NotStarted
                }
                TodayEntry(definition.id, definition.displayName, status)
            }
        }
}
