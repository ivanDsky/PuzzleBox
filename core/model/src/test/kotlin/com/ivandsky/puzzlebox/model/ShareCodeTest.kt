package com.ivandsky.puzzlebox.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShareCodeTest {

    private val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    @Test
    fun `typed input normalises to the canonical code`() {
        val table = mapOf(
            "K7Q2MX" to "K7Q2MX",
            "k7q2mx" to "K7Q2MX",
            "k7q-2mx" to "K7Q2MX",
            "K7Q 2MX" to "K7Q2MX",
            " K7Q - 2MX " to "K7Q2MX",
            "--K7Q2MX--" to "K7Q2MX",
            "O0oO0o" to "000000",
            "IiLl11" to "111111",
            "abcdef" to "ABCDEF",
            "zyxwvt" to "ZYXWVT",
        )
        table.forEach { (input, canonical) ->
            assertEquals(input, canonical, ShareCode.parse(input)?.value)
        }
    }

    @Test
    fun `invalid input parses to null`() {
        val invalid = listOf(
            "",
            "   ",
            "---",
            "K7Q2M",
            "K7Q2MXA",
            "K7Q-2M",
            "K7Q2MU",
            "u7q2mx",
            "K7Q2M!",
            "K7Q_2MX",
            "K7Q\t2MX",
            "K7Q2MÉ",
            "https://puzzlebox-f3356.web.app/p/K7Q2MX",
        )
        invalid.forEach { input -> assertNull(input, ShareCode.parse(input)) }
    }

    @Test
    fun `display splits the code with a dash and link appends it to the prefix`() {
        val code = ShareCode.parse("k7q2mx")!!

        assertEquals("K7Q-2MX", code.display)
        assertEquals("https://puzzlebox-f3356.web.app/p/K7Q2MX", code.link)
        assertEquals(code, ShareCode.parse(code.display))
    }

    @Test
    fun `random codes stay in the alphabet and round trip through their link`() {
        val random = Random(42)
        repeat(1_000) {
            val code = ShareCode.random(random)

            assertEquals(ShareCode.LENGTH, code.value.length)
            assertTrue(code.value, code.value.all { it in alphabet })
            assertEquals(code, ShareCode.fromLink(code.link))
            assertEquals(code, ShareCode.parse(code.display))
        }
    }

    @Test
    fun `random covers the whole alphabet`() {
        val random = Random(7)
        val seen = (1..1_000).flatMap { ShareCode.random(random).value.toList() }.toSet()

        assertEquals(alphabet.toSet(), seen)
    }

    @Test
    fun `accepted links`() {
        val accepted = listOf(
            "https://puzzlebox-f3356.web.app/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app/p/K7Q2MX/",
            "https://puzzlebox-f3356.web.app/p/k7q-2mx",
            "HTTPS://PuzzleBox-F3356.web.app/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app/p/K7Q2MX?utm=share",
            "https://puzzlebox-f3356.web.app/p/K7Q2MX#top",
        )
        accepted.forEach { uri -> assertEquals(uri, "K7Q2MX", ShareCode.fromLink(uri)?.value) }
    }

    @Test
    fun `rejected links`() {
        val rejected = listOf(
            "http://puzzlebox-f3356.web.app/p/K7Q2MX",
            "https://example.com/p/K7Q2MX",
            "https://puzzlebox-f3356.firebaseapp.com/p/K7Q2MX",
            "https://evil.puzzlebox-f3356.web.app/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app.evil.com/p/K7Q2MX",
            "https://user@puzzlebox-f3356.web.app/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app:8443/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app/q/K7Q2MX",
            "https://puzzlebox-f3356.web.app/K7Q2MX",
            "https://puzzlebox-f3356.web.app/p/",
            "https://puzzlebox-f3356.web.app/p/K7Q2MX/extra",
            "https://puzzlebox-f3356.web.app/p/K7Q2MX//",
            "https://puzzlebox-f3356.web.app/x/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app/p/K7Q2M",
            "https://puzzlebox-f3356.web.app/p/K7Q2MU",
            "puzzlebox-f3356.web.app/p/K7Q2MX",
            "K7Q2MX",
            "",
            "not a uri at all",
        )
        rejected.forEach { uri -> assertNull(uri, ShareCode.fromLink(uri)) }
    }

    @Test
    fun `accepted match links`() {
        val accepted = listOf(
            "https://puzzlebox-f3356.web.app/m/K7Q2MX",
            "https://puzzlebox-f3356.web.app/m/k7q2mx",
            "https://puzzlebox-f3356.web.app/m/K7Q2MX/",
            "https://puzzlebox-f3356.web.app/m/K7Q2MX?utm=share",
            "https://puzzlebox-f3356.web.app/m/K7Q2MX#top",
        )
        accepted.forEach { uri -> assertEquals(uri, "K7Q2MX", ShareCode.fromMatchLink(uri)?.value) }
        val code = ShareCode.parse("K7Q2MX")!!
        assertEquals(code, ShareCode.fromMatchLink(code.matchLink))
    }

    @Test
    fun `rejected match links`() {
        val rejected = listOf(
            "https://user@puzzlebox-f3356.web.app/m/K7Q2MX",
            "https://puzzlebox-f3356.web.app:8443/m/K7Q2MX",
            "https://example.com/m/K7Q2MX",
            "http://puzzlebox-f3356.web.app/m/K7Q2MX",
            "https://puzzlebox-f3356.web.app/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app/m/K7Q2MU",
            "https://puzzlebox-f3356.web.app/m/",
        )
        rejected.forEach { uri -> assertNull(uri, ShareCode.fromMatchLink(uri)) }
    }

    @Test
    fun `puzzle and match links are not interchangeable`() {
        assertNull(ShareCode.fromLink("https://puzzlebox-f3356.web.app/m/K7Q2MX"))
    }
}
