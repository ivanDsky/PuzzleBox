package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.model.DailyLink
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static checks on the daily-link hosting page; the test working directory is the module root. */
class DailyPageFilesTest {

    private fun read(path: String): String = File(path).readText()

    private val page get() = read("hosting/p/daily.html")

    @Test
    fun dailyRewriteComesBeforeTheCodeRewriteSoCodesStillReachTheCodePage() {
        val config = read("firebase.json").filterNot { it.isWhitespace() }
        val daily = config.indexOf("{\"source\":\"/p/*/*\",\"destination\":\"/p/daily.html\"}")
        val code = config.indexOf("{\"source\":\"/p/**\",\"destination\":\"/p/index.html\"}")
        assertTrue("daily rewrite missing", daily >= 0)
        assertTrue("code rewrite missing", code >= 0)
        assertTrue("/p/*/* must precede /p/**", daily < code)
    }

    @Test
    fun pageHasOnlyTheSameOriginDailyModuleScript() {
        val srcs = Regex("<script[^>]*\\bsrc\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .findAll(page).map { it.groupValues[1] }.toList()
        assertEquals(listOf("/p/daily.js"), srcs)
        assertFalse(Regex("<link[^>]*stylesheet", RegexOption.IGNORE_CASE).containsMatchIn(page))
        assertTrue(File("hosting/p/daily.js").isFile)
    }

    @Test
    fun pageHasOgCardDarkModeNoScriptLineAndInvalidCopy() {
        assertTrue(page.contains("<meta property=\"og:title\" content=\"A PuzzleBox daily puzzle\">"))
        assertTrue(page.contains("<meta property=\"og:description\""))
        assertTrue(page.contains("<h1 id=\"heading\">A PuzzleBox daily puzzle</h1>"))
        assertTrue(page.contains("@media (prefers-color-scheme: dark)"))
        assertTrue(
            Regex("<noscript>[\\s\\S]*?Open this link on a phone with PuzzleBox to play it[\\s\\S]*?</noscript>")
                .containsMatchIn(page),
        )
        assertTrue(page.contains("This link doesn't contain a valid PuzzleBox daily puzzle"))
    }

    @Test
    fun pageLinksToTheLatestGitHubReleaseInANewTab() {
        val tag = Regex("<a[^>]*\\bhref\\s*=\\s*\"https://github\\.com/[^\"]*\"[^>]*>").findAll(page).single().value
        assertTrue(tag.contains("href=\"https://github.com/ivanDsky/PuzzleBox/releases/latest\""))
        assertTrue(tag.contains("rel=\"noopener\""))
        assertTrue(tag.contains("target=\"_blank\""))
    }

    @Test
    fun dailyLinkParseAgreesWithEverySharedVector() {
        val vectors = Regex("\\{\"path\": \"([^\"]*)\", \"game\": (null|\"[^\"]*\"), \"day\": (null|\"[^\"]*\")\\}")
            .findAll(read("hosting-test/daily-link-vectors.json"))
            .map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()
        assertTrue(vectors.size > 20)
        vectors.forEach { (path, game, day) ->
            val parsed = DailyLink.parse("https://puzzlebox-f3356.web.app$path")
            if (game == "null") {
                assertNull("expected null for $path", parsed)
            } else {
                assertEquals(path, game.trim('"'), parsed?.gameId?.value)
                assertEquals(path, day.trim('"'), parsed?.day?.toString())
            }
        }
    }
}
