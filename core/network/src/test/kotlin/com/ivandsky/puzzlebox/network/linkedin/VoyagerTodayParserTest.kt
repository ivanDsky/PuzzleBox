package com.ivandsky.puzzlebox.network.linkedin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoyagerTodayParserTest {

    private fun body(elements: String) =
        """{"data":{"data":{"identityDashGamesByTodaysGame":{"*elements":$elements}}}}"""

    @Test
    fun `today's game gives the profile id`() {
        val response = body("""["urn:li:fsd_game:(ACoAAFAKE0001,6,812)"]""")

        assertEquals("ACoAAFAKE0001", VoyagerTodayParser.profileId(response))
    }

    @Test
    fun `a missing, empty or non-game element is a shape mismatch`() {
        val bodies = listOf(
            """{"data":{"data":{"identityDashGamesByTodaysGame":{}}}}""",
            body("[]"),
            body("""["urn:li:fsd_profile:ACoAAFAKE0001"]"""),
            body("""[42]"""),
            "not json",
        )

        bodies.forEach { assertNull(it, VoyagerTodayParser.profileId(it)) }
    }
}
