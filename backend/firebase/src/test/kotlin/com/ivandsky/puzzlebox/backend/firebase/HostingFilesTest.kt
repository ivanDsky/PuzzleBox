package com.ivandsky.puzzlebox.backend.firebase

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    }

    @Test
    fun landingPageHasNoExternalScripts() {
        val page = read("hosting/p/index.html")
        assertTrue(page.contains("<script>"))
        assertFalse(Regex("<script[^>]*\\bsrc\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(page))
        assertFalse(Regex("<link[^>]*stylesheet", RegexOption.IGNORE_CASE).containsMatchIn(page))
    }
}
