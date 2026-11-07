package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.editor.QueensCrownIssue
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraftAnalyzer
import com.ivandsky.puzzlebox.games.queens.editor.QueensPublishGates
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queensui.editor.QueensFixRow
import com.ivandsky.puzzlebox.games.queensui.editor.QueensFixRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every id the Queens engine can hand the UI has copy (00-interfaces P-4): a rung added to
 * [QueensLadder.Default] without a [QueensRungCopy] caption fails here, and so does a reason
 * ([QueensDraftAnalyzer.reasonIds]), block ([QueensPublishGates.blockIds]) or crown issue
 * ([QueensCrownIssue.ids]) without a fix-list row (25).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueensCopyCoverageTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `every rung of the default ladder has a caption`() {
        val rungs = QueensLadder.Default.rungs
        assertTrue("the default ladder lists its rungs", rungs.isNotEmpty())

        val missing = rungs.filter { QueensRungCopy.caption(it.captionKey) == null }.map { it.id }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `an unknown caption key has no caption`() {
        assertEquals(null, QueensRungCopy.caption("queens.hint.rung.unknown"))
    }

    @Test
    fun `every reason, block and crown issue renders a non-empty fix row`() {
        val ids = QueensDraftAnalyzer.reasonIds + QueensPublishGates.blockIds + QueensCrownIssue.ids
        rule.setContent {
            PuzzleBoxTheme {
                QueensFixRows(ids.map { QueensFixRow(it, count = 2, region = 0, cells = setOf(0)) }, size = 4, onSelect = {})
            }
        }

        val missing = ids.filter { id -> rowText("queens.editor.fix.$id").isBlank() }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `an id without copy draws no row`() {
        rule.setContent {
            PuzzleBoxTheme { QueensFixRows(listOf(QueensFixRow("queens.unknown")), size = 4, onSelect = {}) }
        }

        assertTrue(rule.onAllNodesWithTag("queens.editor.fix.queens.unknown").fetchSemanticsNodes().isEmpty())
    }

    private fun rowText(tag: String): String =
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .joinToString { it.text }
}
