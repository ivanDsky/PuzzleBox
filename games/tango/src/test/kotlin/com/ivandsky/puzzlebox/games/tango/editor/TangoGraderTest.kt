package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec A9 for Tango: a Unique board's label is the level of the hardest hint-ladder rung its
 * walk uses, one fixture per label; a Unique board the walk cannot finish needs guessing, has
 * no label and is blocked from publishing. Easy, Medium and Hard are cut from the bundled
 * sample's solution, Hard line completion from the real daily #758; the stuck fixture is a
 * generated unique board no rung forces.
 */
class TangoGraderTest {

    private val fixtures = listOf(
        Fixture(
            "markers only",
            ".SM.MM/SSMSMM/MMSM.S/SSMMSM/MMSSM./MMSMSS",
            "0D=,3D=,16D=,29D=",
            TangoRung.MARKER_BINDING,
            Difficulty.Level.Easy,
        ),
        Fixture(
            "three in a row",
            "SS..MM/SSMSMM/MM.MSS/SSMMSM/MMSSMS/MMSMSS",
            "",
            TangoRung.THREE_IN_A_ROW,
            Difficulty.Level.Medium,
        ),
        Fixture(
            "line count",
            "..M.S./.S..../....../..MM../....../..M...",
            "21Dx,4D=,8Rx,28D=,12R=",
            TangoRung.LINE_FULL,
            Difficulty.Level.Hard,
        ),
        Fixture(
            "line fills",
            "MMSSMS/MSSMSM/....../....../....../......",
            "24R=,26R=,28Rx,30R=,32Rx,34R=",
            TangoRung.LINE_FILLS,
            Difficulty.Level.Hard,
        ),
    )

    private val stuck = Fixture(
        "unique board no rung forces",
        "S...../.S..../...M../.....M/....../....SS",
        "1D=,3D=,5D=,12Dx,13Dx,16D=,19Rx",
        TangoRung.LINE_FILLS,
        Difficulty.Level.Hard,
    )

    @Test
    fun `each label fixture grades to its rung without getting stuck`() {
        fixtures.forEach { fixture ->
            assertEquals(fixture.name, TangoGrade(fixture.rung, stuck = false), TangoGrader.grade(fixture.puzzle()))
        }
    }

    @Test
    fun `each label fixture is Unique with its level and no publish block`() {
        fixtures.forEach { fixture ->
            val draft = fixture.draft()

            val verdict = TangoDraftAnalyzer.analyze(draft, Budget.Unlimited) as Verdict.Unique

            assertEquals(fixture.name, fixture.level, verdict.difficulty?.level)
            assertEquals(fixture.name, fixture.rung, verdict.difficulty?.hardestRung)
            assertEquals(fixture.name, false, verdict.needsGuessing)
            assertEquals(fixture.name, emptyList<PublishBlock>(), TangoPublishGates.blocks(draft, verdict))
        }
    }

    @Test
    fun `the stuck fixture is Unique needing guessing, without a label, and blocked`() {
        val draft = stuck.draft()

        val verdict = TangoDraftAnalyzer.analyze(draft, Budget.Unlimited)

        assertEquals(TangoGrade(stuck.rung, stuck = true), TangoGrader.grade(stuck.puzzle()))
        val unique = verdict as Verdict.Unique
        assertNull(unique.difficulty)
        assertTrue(unique.needsGuessing)
        assertEquals(listOf(PublishBlock(PublishBlock.NEEDS_GUESSING)), TangoPublishGates.blocks(draft, verdict))
    }

    @Test
    fun `a board the givens already fill grades to no rung and is not stuck`() {
        val full = TangoPuzzle(
            TangoTestSupport.variant,
            TangoTestSupport.n,
            TangoTestSupport.solution.withIndex().associate { (idx, cell) -> idx to cell },
            emptyList(),
        )

        assertEquals(TangoGrade(hardest = null, stuck = false), TangoGrader.grade(full))
    }

    @Test
    fun `a Unique verdict that needs no guessing adds no block`() {
        val verdict = Verdict.Unique(Solution(TangoTestSupport.solution.map { it.ordinal }))

        assertEquals(emptyList<PublishBlock>(), TangoPublishGates.blocks(stuck.draft(), verdict))
    }

    @Test
    fun `a verdict that is not Unique adds no guessing block`() {
        val draft = stuck.draft()

        assertEquals(emptyList<PublishBlock>(), TangoPublishGates.blocks(draft, Verdict.Undetermined))
        assertEquals(emptyList<PublishBlock>(), TangoPublishGates.blocks(draft, Verdict.None(emptySet())))
    }

    @Test
    fun `blockIds names every block the gates can emit`() {
        assertEquals(setOf(PublishBlock.NEEDS_GUESSING), TangoPublishGates.blockIds)
    }

    @Test
    fun `the editor publishes through the Tango gates`() {
        val draft = stuck.draft()
        val verdict = TangoEditor.analyze(draft, Budget.Unlimited)

        assertEquals(TangoPublishGates.blocks(draft, verdict), TangoEditor.publishBlocks(draft, verdict))
    }

    @Test
    fun `the same board always grades the same`() {
        (fixtures + stuck).forEach { fixture ->
            val draft = fixture.draft()

            assertEquals(fixture.name, TangoGrader.grade(fixture.puzzle()), TangoGrader.grade(fixture.puzzle()))
            assertEquals(
                fixture.name,
                TangoDraftAnalyzer.analyze(draft, Budget.Unlimited),
                TangoDraftAnalyzer.analyze(draft, Budget.Unlimited),
            )
        }
    }

    @Test
    fun `rungs are declared in difficulty order`() {
        assertEquals(
            listOf(Difficulty.Level.Easy, Difficulty.Level.Medium, Difficulty.Level.Hard, Difficulty.Level.Hard),
            TangoRung.entries.map { it.level },
        )
    }

    /** [rows] is six '/'-joined strings of S, M or '.'; [markers] is cell, R or D, '=' or 'x'. */
    private class Fixture(
        val name: String,
        rows: String,
        markers: String,
        val rung: TangoRung,
        val level: Difficulty.Level,
    ) {
        private val givens: Map<Int, TangoCell> = rows.split('/').joinToString("").withIndex()
            .mapNotNull { (idx, ch) ->
                when (ch) {
                    'S' -> idx to TangoCell.SUN
                    'M' -> idx to TangoCell.MOON
                    else -> null
                }
            }.toMap()

        private val markerList: List<TangoMarker> = markers.split(',').filter { it.isNotEmpty() }.map { token ->
            TangoMarker(
                cell = token.dropLast(2).toInt(),
                side = if (token[token.length - 2] == 'R') TangoEdgeSide.RIGHT else TangoEdgeSide.DOWN,
                kind = if (token.last() == '=') TangoMarkerKind.SAME else TangoMarkerKind.OPPOSITE,
            )
        }

        fun draft(): TangoDraft = TangoDraft(givens, markerList)

        fun puzzle(): TangoPuzzle =
            TangoPuzzle(VariantId("tango/n${TangoDraft.N}"), TangoDraft.N, givens, markerList)
    }
}
