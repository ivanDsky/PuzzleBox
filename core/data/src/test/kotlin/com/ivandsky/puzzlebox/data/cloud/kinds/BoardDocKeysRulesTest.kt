package com.ivandsky.puzzlebox.data.cloud.kinds

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The board keys in `firestore.rules` are the keys in [BoardDocKeys]: a key added to either side alone makes every
 * board write refused, and the rules are not run against the app's encoder anywhere else. `BoardDocKeys` is internal
 * to this module, so the check lives here and reads the rules file from the repository.
 */
class BoardDocKeysRulesTest {

    private val rules = File("../../backend/firebase/firestore.rules").readText()

    @Test
    fun `a board doc's keys are the keys of the rules' board list, updatedAt aside`() {
        val allowed = keysIn(functionBody("validBoardDoc"), Regex("""d\.keys\(\)\.hasOnly\(\[(.*?)]\)""", RegexOption.DOT_MATCHES_ALL))
        assertEquals(BoardDocKeys.DOC + "updatedAt", allowed)
    }

    @Test
    fun `a board row's keys are the keys of the rules' per-row list`() {
        val allowed = keysIn(functionBody("validBoardRows"), Regex("""let k = \[(.*?)];""", RegexOption.DOT_MATCHES_ALL))
        assertEquals(BoardDocKeys.ROW, allowed)
    }

    private fun keysIn(body: String, list: Regex): Set<String> =
        Regex("""'([^']+)'""").findAll(list.find(body)!!.groupValues[1]).map { it.groupValues[1] }.toSet()

    /** The text of `function [name](...)` through the first closing brace at the same indent. */
    private fun functionBody(name: String): String {
        val start = rules.indexOf("function $name(")
        check(start >= 0) { "firestore.rules has no function $name" }
        val indent = rules.substring(rules.lastIndexOf('\n', start) + 1, start)
        val end = rules.indexOf("\n$indent}", start)
        return rules.substring(start, end)
    }
}
