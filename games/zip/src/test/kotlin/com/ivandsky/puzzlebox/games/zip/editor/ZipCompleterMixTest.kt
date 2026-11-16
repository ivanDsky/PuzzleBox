package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.ZipGenerator
import com.ivandsky.puzzlebox.games.zip.ZipTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The mix of walls and numbers Finish for me leaves on a Zip board (ticket 97): both, in the
 * proportion of the captured LinkedIn dailies. Those have 4 walls to 6 numbers at the least
 * walled (#592, 6×6) and 16 walls to 8 numbers at the most (8×8), so 2/3 to 2 walls per number.
 * Seeded and unbudgeted, so every run is the same.
 */
class ZipCompleterMixTest {

    @Test
    fun `nine of ten sampled drafts finish with a wall and two numbers at every size`() {
        for ((size, presses) in sampled) {
            val mixed = presses.count { (_, done) -> done != null && done.walls.isNotEmpty() && done.waypoints.size >= 2 }

            assertTrue("n$size: $mixed of ${presses.size}", mixed * 10 >= presses.size * 9)
        }
    }

    @Test
    fun `nine of ten sampled drafts finish with the walls per number of a daily`() {
        for ((size, presses) in sampled) {
            // One wall of slack: the piece that makes the board unique lands wherever the mix then stands.
            val daily = presses.count { (_, done) ->
                done != null && done.walls.size + 1 >= DAILY_LEAST * done.waypoints.size &&
                    done.walls.size - 1 <= DAILY_MOST * done.waypoints.size
            }

            assertTrue("n$size: $daily of ${presses.size}", daily * 10 >= presses.size * 9)
        }
    }

    @Test
    fun `a sampled draft that finishes is unique on the path it is drawn with`() {
        for ((draft, done) in finished()) {
            assertEquals("$draft", Verdict.Unique(Solution(done.path)), ZipDraftAnalyzer.analyze(done, Budget.Unlimited))
        }
    }

    @Test
    fun `a sampled draft that finishes keeps every author wall, number and path step`() {
        for ((draft, done) in finished()) {
            assertTrue("$draft", done.walls.containsAll(draft.walls))
            assertEquals("$draft", draft.waypoints, done.waypoints.filter { it in draft.waypoints })
            assertEquals("$draft", draft.path, done.path.take(draft.path.size))
        }
    }

    @Test
    fun `presses on one draft give boards from both ends of the daily mix`() {
        val draft = ZipDraftCuts.walked(size = 6, length = ZipCompleter.MIN_PATH, Random(6))
        val random = Random(97)

        val mixes = List(PRESSES) { (ZipCompleter.complete(draft, random, Budget.Unlimited) as Completion.Done).draft }
            .map { it.walls.size.toDouble() / it.waypoints.size }

        assertTrue("$mixes", mixes.min() < 1.0)
        assertTrue("$mixes", mixes.max() > 1.5)
    }

    private fun finished(): List<Pair<ZipDraft, ZipDraft>> {
        val done = sampled.values.flatten().mapNotNull { (draft, done) -> done?.let { draft to it } }
        assertTrue("no sampled draft finished", done.isNotEmpty())
        return done
    }

    private companion object {
        const val DAILY_LEAST = 2.0 / 3
        const val DAILY_MOST = 2.0
        const val SAMPLES = 10
        const val PRESSES = 12

        /**
         * Per size 5..8, [SAMPLES] drafts with the board Finish made of each (null: it gave up).
         * Every other one is a walk of the minimum length on a blank board that readiness lets
         * through. The rest are the first cells of a generated board's path, three to eleven of
         * them, one numbered, with none, one or two of that board's walls.
         *
         * The generator seeds are [ZipCompleterTest]'s: its walk has no node limit and does not
         * come back from some size 7 seeds.
         */
        val sampled: Map<Int, List<Pair<ZipDraft, ZipDraft?>>> by lazy {
            (5..8).associateWith { size ->
                val board = checkNotNull(ZipGenerator(Random(size * 100)).nextUnique(ZipTestSupport.variantOf(size)))
                List(SAMPLES) { sample ->
                    val random = Random(size * 1000 + sample)
                    val draft = if (sample % 2 == 0) {
                        generateSequence { ZipDraftCuts.walked(size, ZipCompleter.MIN_PATH, random) }
                            .first { ZipCompleter.readiness(it) == FinishReadiness.Ready }
                    } else {
                        ZipDraftCuts.drawn(board, length = ZipCompleter.MIN_PATH + sample, marks = 1, walls = sample % 3, random)
                    }
                    draft to (ZipCompleter.complete(draft, random, Budget.Unlimited) as? Completion.Done)?.draft
                }
            }
        }
    }
}
