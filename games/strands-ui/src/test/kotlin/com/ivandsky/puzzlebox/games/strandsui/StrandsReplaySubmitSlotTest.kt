package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The word line a replay frame shows above the board: the submitted word read as live reads it, empty for any other step. */
class StrandsReplaySubmitSlotTest {

    private val start = StrandsState(StrandsUiFixtures.puzzle)

    @Test
    fun `the first frame and a hint show an empty line`() {
        assertEquals(WordSlot.Empty, submittedSlotOf(null, start))
        assertEquals(WordSlot.Empty, submittedSlotOf(start, start.copy(activeHint = ActiveHint("PIES", HintStage.Outlined))))
    }

    @Test
    fun `a theme word shows itself`() {
        assertEquals(
            WordSlot.Verdict("PIES", VerdictKind.Theme, seq = 1),
            submittedSlotOf(start, start.copy(found = listOf("PIES"))),
        )
    }

    @Test
    fun `the spangram reads as live reads it`() {
        val before = start.copy(found = listOf("PIES"))
        assertEquals(
            WordSlot.Verdict(StrandsCopy.SPANGRAM, VerdictKind.Spangram, seq = 2),
            submittedSlotOf(before, before.copy(found = listOf("PIES", "BAKE"))),
        )
    }

    @Test
    fun `a non-theme word shows the word though it marks no cell`() {
        val before = start.copy(found = listOf("PIES"), nonTheme = setOf("PIKE"))
        assertEquals(
            WordSlot.Verdict("RAKE", VerdictKind.NonTheme, seq = 3),
            submittedSlotOf(before, before.copy(nonTheme = before.nonTheme + "RAKE")),
        )
    }
}
