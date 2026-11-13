package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.data.cloud.CloudSpaceKinds
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Delete my cloud data" erases [CloudSpaceKinds.ALL], so that list must be exactly the kinds the `isSpaceKind()` rule of
 * `backend/firebase/firestore.rules` lets an account write: a kind missing here would survive an erase, a kind missing
 * from the rules would only waste a read.
 */
class CloudSpaceKindsRulesTest {

    @Test
    fun `the erase kind list equals the rules account block kinds`() {
        val rules = File("../backend/firebase/firestore.rules").readText()
        val body = Regex("""function\s+isSpaceKind\(\)\s*\{\s*return\s+kind\s+in\s*\[([^\]]*)]""")
            .find(rules)
            ?.groupValues?.get(1)
            ?: error("firestore.rules has no isSpaceKind() kind list")
        val inRules = Regex("""'([^']+)'""").findAll(body).map { it.groupValues[1] }.toList()

        assertEquals(inRules.sorted(), CloudSpaceKinds.ALL.sorted())
    }
}
