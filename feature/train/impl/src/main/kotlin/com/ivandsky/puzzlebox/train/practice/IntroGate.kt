package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * The technique intro's side of a practice (spec §2.7): finds it ([find]), says whether it is being replayed from the
 * board's "How it works" chip ([replaying]), and tells the one-time [hint] when an intro is left. Replaying is not a
 * practice state: the board stays as it is behind it, so closing the replay gives the same board back.
 */
internal class IntroGate(
    private val game: TrainerGame,
    private val source: TrainerPuzzleSource,
    private val dispatchers: DispatcherProvider,
    private val hint: IntroHint,
) {
    private val mutableIntro = MutableStateFlow<TechniqueIntro?>(null)
    private val mutableReplaying = MutableStateFlow(false)

    /** The intro of the technique being practiced, null while it is being looked for or when it has none. */
    val intro: StateFlow<TechniqueIntro?> = mutableIntro.asStateFlow()

    /** The intro is up over a live board. */
    val replaying: StateFlow<Boolean> = mutableReplaying.asStateFlow()

    /**
     * The intro of [technique]: per variant the first demo on the drill puzzles, which are walked in the order a chapter
     * opens them (the source's, newest first, then the technique's bundled ones); a variant with no demo on any is left
     * out. Null when no variant has one. Nothing is fetched.
     */
    suspend fun find(technique: TechniqueId): TechniqueIntro? {
        mutableIntro.value = null
        mutableReplaying.value = false
        val variants = game.variants(technique)
        val found = arrayOfNulls<Pair<Puzzle, IntroDemo>>(variants.size)
        suspend fun look(puzzle: Puzzle): Boolean = withContext(dispatchers.default) {
            found.indices.forEach { v -> if (found[v] == null) found[v] = game.demo(puzzle, technique, v)?.let { puzzle to it } }
            found.all { it != null }
        }
        if (source.puzzles(game.gameId).firstOrNull { look(it) } == null) {
            withContext(dispatchers.default) { game.bundledPuzzles(technique) }.firstOrNull { look(it) }
        }
        val entries = found.mapIndexedNotNull { v, hit -> hit?.let { IntroEntry(variants[v], it.second) } }
        val first = found.firstNotNullOfOrNull { it?.first } ?: return null
        return TechniqueIntro(entries, first).also { mutableIntro.value = it }
    }

    /** The chip: puts the intro up over the board. Ignored when the technique has none. */
    fun show() {
        if (mutableIntro.value == null) return
        hint.dismiss()
        mutableReplaying.value = true
    }

    /** Closes the replay: the board is as it was. */
    fun close() {
        mutableReplaying.value = false
    }

    /** The opening intro was left, by Skip or Start practice: the board is next. */
    fun handedOver() = hint.introLeft()

    /** The one-time bubble that points at the chip is up. */
    val hintVisible: StateFlow<Boolean> get() = hint.visible

    /** The bubble's OK. */
    fun dismissHint() = hint.dismiss()
}
