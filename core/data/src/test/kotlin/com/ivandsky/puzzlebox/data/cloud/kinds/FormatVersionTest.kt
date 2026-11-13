package com.ivandsky.puzzlebox.data.cloud.kinds

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The synced kinds share one `formatVersion`, and it is the one `firestore.rules` checks on every space doc. */
class FormatVersionTest {

    @Test
    fun `the shared const equals the version the rules check`() {
        val rules = File("../../backend/firebase/firestore.rules").readText()
        val checked = Regex("""function validSpaceDoc\(\)[\s\S]*?d\.formatVersion == (\d+)""").find(rules)
            ?.groupValues?.get(1)?.toLong()
            ?: error("firestore.rules has no formatVersion check in validSpaceDoc()")

        assertEquals(checked, CLOUD_FORMAT_VERSION)
    }
}
