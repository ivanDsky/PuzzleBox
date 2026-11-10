package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.model.ShareCode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static checks on the Hosting files; the test working directory is the module root. */
class HostingFilesTest {

    private fun read(path: String): String = File(path).readText()

    private fun statementFor(assetLinks: String, packageName: String): String {
        val statements = assetLinks.split("\"relation\"").drop(1)
        return statements.single { it.contains("\"package_name\": \"$packageName\"") }
    }

    private fun fingerprints(statement: String): List<String> =
        Regex("\"([0-9A-F]{2}(?::[0-9A-F]{2}){31})\"").findAll(statement).map { it.groupValues[1] }.toList()

    @Test
    fun assetLinksDelegatesBothPackagesWithTheirCertificates() {
        val assetLinks = read("hosting/.well-known/assetlinks.json")
        val expected = mapOf(
            "com.ivandsky.puzzlebox" to
                "8E:88:0D:BA:3E:AB:CB:7C:8E:97:A3:35:83:5B:3D:69:07:9B:5C:16:A8:FC:74:24:B7:1C:58:03:72:05:F7:B9",
            "com.ivandsky.puzzlebox.debug" to
                "EF:07:04:94:AA:5B:2C:5F:44:DB:B2:B5:B0:E8:CD:4D:82:C1:94:EB:7F:08:B9:10:7F:59:B9:70:66:07:91:3B",
        )
        expected.forEach { (packageName, sha) ->
            val statement = statementFor(assetLinks, packageName)
            assertTrue(statement.contains("\"delegate_permission/common.handle_all_urls\""))
            assertTrue(statement.contains("\"namespace\": \"android_app\""))
            assertEquals(listOf(sha), fingerprints(statement))
        }
    }

    @Test
    fun firebaseJsonServesHostingAndRewritesCodePathsWithFirestoreUnchanged() {
        val config = read("firebase.json").filterNot { it.isWhitespace() }
        assertTrue(config.contains("\"firestore\":{\"rules\":\"firestore.rules\",\"indexes\":\"firestore.indexes.json\"}"))
        assertTrue(config.contains("\"hosting\":{\"public\":\"hosting\""))
        assertTrue(config.contains("{\"source\":\"/p/**\",\"destination\":\"/p/index.html\"}"))
        assertTrue(config.contains("{\"source\":\"/m/**\",\"destination\":\"/m/index.html\"}"))
    }

    @Test
    fun hostingIgnoresDsStoreButKeepsWellKnownDeployable() {
        val ignore = Regex("\"ignore\":\\[([^\\]]*)\\]")
            .find(read("firebase.json").filterNot { it.isWhitespace() })!!.groupValues[1]
        val patterns = Regex("\"([^\"]*)\"").findAll(ignore).map { it.groupValues[1] }.toList()
        assertTrue(patterns.contains("**/.DS_Store"))
        assertTrue(patterns.none { it.contains(".well-known") || it == "**/.*" })
        assertTrue(File("hosting/.well-known/assetlinks.json").isFile)
    }

    private val pages = listOf("p", "m")

    @Test
    fun landingPageHasOnlySameOriginModuleScript() = pages.forEach { dir ->
        val page = read("hosting/$dir/index.html")
        val srcs = Regex("<script[^>]*\\bsrc\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .findAll(page).map { it.groupValues[1] }.toList()
        assertEquals("$dir", listOf("/$dir/code.js"), srcs)
        assertFalse("$dir", Regex("<link[^>]*stylesheet", RegexOption.IGNORE_CASE).containsMatchIn(page))
    }

    @Test
    fun landingPageHasOgCardDarkModeAndNoScriptSteps() = pages.forEach { dir ->
        val page = read("hosting/$dir/index.html")
        val ogTitle = if (dir == "p") "A PuzzleBox puzzle from a friend" else "A PuzzleBox duel from a friend"
        val steps = if (dir == "p") "Enter a code" else "Enter code"
        assertTrue("$dir", page.contains("<meta property=\"og:title\" content=\"$ogTitle\">"))
        assertTrue("$dir", page.contains("<meta property=\"og:description\""))
        assertTrue("$dir", page.contains("@media (prefers-color-scheme: dark)"))
        assertTrue("$dir", Regex("<noscript>[\\s\\S]*?$steps[\\s\\S]*?</noscript>").containsMatchIn(page))
        assertTrue("$dir", page.contains("This link doesn't contain a valid PuzzleBox code"))
    }

    @Test
    fun landingPageLinksToTheLatestGitHubRelease() = pages.forEach { dir ->
        val page = read("hosting/$dir/index.html")
        val hrefs = Regex("<a[^>]*\\bhref\\s*=\\s*\"([^\"]+)\"[^>]*>").findAll(page).map { it.groupValues[1] }.toList()
        val link = hrefs.single { Regex("https://github\\.com/[^/]+/[^/]+/.*").matches(it) }
        val url = java.net.URI(link)
        assertEquals("$dir", "github.com", url.host)
        assertTrue("$dir", url.path.startsWith("/ivanDsky/PuzzleBox/"))
        val tag = Regex("<a[^>]*\\bhref\\s*=\\s*\"[^\"]*github\\.com[^\"]*\"[^>]*>").find(page)!!.value
        assertTrue("$dir", tag.contains("rel=\"noopener\""))
        assertTrue("$dir", tag.contains("target=\"_blank\""))
    }

    @Test
    fun shareCodeParseAgreesWithEverySharedVector() {
        val vectors = read("hosting-test/share-code-vectors.json")
        val entries = Regex("\\{\"input\": \"((?:[^\"\\\\]|\\\\.)*)\", \"code\": (null|\"[^\"]*\")\\}")
            .findAll(vectors).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue(entries.isNotEmpty())
        entries.forEach { (input, expected) ->
            val parsed = ShareCode.parse(input)
            if (expected == "null") {
                assertNull("expected null for $input", parsed)
            } else {
                assertEquals(expected.trim('"'), parsed?.value)
            }
        }
    }
}
