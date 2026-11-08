package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.repository.DAILY_PUBLISH_CAP
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The quota bump in firestore.rules must stay in step with the app's daily publish cap. */
class FirestoreRulesCapTest {

    @Test
    fun quotaBumpBoundEqualsTheDailyPublishCap() {
        val bounds = Regex("""^\s*&& after\.count <= (\d+);$""", RegexOption.MULTILINE)
            .findAll(File("firestore.rules").readText())
            .map { it.groupValues[1].toInt() }
            .toList()
        assertEquals(listOf(DAILY_PUBLISH_CAP), bounds)
    }
}
