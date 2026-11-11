package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the user rule: theme words and the spangram need not come from the dictionary. The
 * dictionary only informs the editor's word check; it never gates the verdict or publishing.
 */
class StrandsNonDictionaryWordsTest {
    private val cols = StrandsDraft.COLS

    /** One row per word, in the letters, so each word's path spells it. */
    private val words = List(StrandsDraft.ROWS) { "${'Q' + it}".repeat(cols) }

    private val draft = StrandsDraft(
        letters = words.joinToString(""),
        clue = "Made up",
        paths = words.mapIndexed { row, word -> StrandsDraftPath((0 until cols).map { row * cols + it }, word) },
        spangram = 0,
    )

    @Test
    fun `the words are outside the dictionary and the blocklist and fit the word shape`() {
        words.forEach {
            assertFalse("$it is a dictionary word", StrandsDictionary.contains(it))
            assertFalse("$it is blocklisted", StrandsDictionary.isBlocklisted(it))
            assertTrue(it.length in 4..24 && it.all { c -> c in 'A'..'Z' })
        }
    }

    @Test
    fun `a board of words outside the dictionary is Unique and publishes with no block`() {
        val verdict = StrandsDraftAnalyzer.analyze(draft, Budget.Unlimited)

        assertTrue("verdict $verdict", verdict is Verdict.Unique)
        assertEquals(emptyList<PublishBlock>(), StrandsPublishGates.blocks(draft, verdict))
    }
}
