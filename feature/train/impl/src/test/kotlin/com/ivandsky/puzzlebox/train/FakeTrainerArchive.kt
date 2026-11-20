package com.ivandsky.puzzlebox.train

import com.ivandsky.puzzlebox.data.trainer.ArchiveLoad
import com.ivandsky.puzzlebox.data.trainer.TrainerArchive
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import kotlinx.coroutines.CompletableDeferred

/**
 * A [TrainerArchive] a test scripts. [cached] are the numbers whose puzzle is on the device; a number not scripted
 * loads its own puzzle (a [TestGames.puzzle] tagged with the number) and is cached from then on, as a fetched puzzle
 * is. [script] queues the outcomes of a number's loads, the last one repeating; a mismatched number is skipped by
 * [onDevice] from then on, as the real archive does. [loads] records every [load] call by number, and [fetches] those
 * whose number was not [cached] when the call came, the ones the real archive would fetch rather than serve from its
 * cache, so a test counts the fetches a tap made. [gate], when set, holds every [load] until it completes. [onLoaded]
 * sees each puzzle a load gave.
 */
internal class FakeTrainerArchive : TrainerArchive {

    val cached = mutableSetOf<Int>()
    val loads = mutableListOf<Int>()
    val fetches = mutableListOf<Int>()
    var gate: CompletableDeferred<Unit>? = null
    var onLoaded: (Puzzle) -> Unit = {}
    private val outcomes = mutableMapOf<Int, ArrayDeque<ArchiveLoad>>()
    private val rejected = mutableSetOf<Int>()

    fun script(number: Int, vararg outcome: ArchiveLoad) {
        outcomes[number] = ArrayDeque(outcome.toList())
    }

    override suspend fun onDevice(game: GameId, technique: TechniqueId, numbers: List<Int>): List<Int> =
        numbers.filter { it in cached && it !in rejected }

    override suspend fun load(game: GameId, technique: TechniqueId, number: Int): ArchiveLoad {
        loads += number
        if (number !in cached) fetches += number
        gate?.await()
        val queue = outcomes[number]
        val outcome = when {
            queue == null || queue.isEmpty() -> ArchiveLoad.Loaded(number, TestGames.puzzle(game, number.toByte()))
            queue.size == 1 -> queue.first()
            else -> queue.removeFirst()
        }
        when (outcome) {
            is ArchiveLoad.Loaded -> {
                cached += number
                onLoaded(outcome.puzzle)
            }
            is ArchiveLoad.Mismatch -> rejected += number
            is ArchiveLoad.CantLoad -> Unit
        }
        return outcome
    }
}
