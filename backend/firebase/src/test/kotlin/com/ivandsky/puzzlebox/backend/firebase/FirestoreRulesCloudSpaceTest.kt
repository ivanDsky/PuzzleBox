package com.ivandsky.puzzlebox.backend.firebase

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-0029 §1, spec SC14: `users/{uid}/{kind}/{docId}` is a new owner-only block, and every rule that
 * existed before it stays byte-identical. The pinned SHA-256 values are of the text on origin/main before
 * the block was added (a block is its `match` line through its closing `    }`; the helpers are the
 * service-level functions through the end of the service body). The per-kind key lists are pinned here as
 * literals because the kind tickets own their codecs; a kind that changes its doc keys changes the rules
 * and this test in the same PR.
 */
class FirestoreRulesCloudSpaceTest {

    private val rules = File("firestore.rules").readText().lines()

    @Test
    fun `existing blocks, deny-all and helpers are byte-identical to before the account space`() {
        val pinned = mapOf(
            "match /{document=**} {" to "2fa7810103dc8eb3f4050c94653a46e8c3ed08cf3536292a3963424eadffd1e9",
            "match /leaderboards/{boardId}/entries/{uid} {" to
                "d2eea56b5561c6dd1b4cb90170ff9fff05b277b2797577c87758aa6419ce048c",
            "match /puzzles/{code} {" to "c3256ffb26eebe2e8e182f3681e1cb3e8a8f33d5b95fafb584896331126c2e34",
            "match /puzzles/{code}/entries/{uid} {" to
                "09382f2ceb2f5fcaccd2b22f12d2e501898ec1715275abe390fbfd81ee7f60cb",
            "match /authors/{uid} {" to "5f8b57cc9972b50cac40bb7c32df8b6ed5ec827bc6e44a7755e7190b2817470f",
            "match /matches/{code} {" to "270c794c3b61d2f7121d08b58f1a547d2baed867679a45dd428d32cf8e23ceb0",
            "match /matches/{code}/results/{resultId} {" to
                "1db45647cbe969e8b531519ab3b2da77be15beb20de4d7687903ed4f9ebcd2ee",
            "match /matchQuota/{uid} {" to "4c170701ba8e9ae657e1c959b94add5174052f4416b7c52e81eaeccf7dd3253d",
        )
        val blocks = topLevelBlocks()
        assertEquals(
            "the rules hold exactly the pinned blocks plus the account space",
            pinned.keys + SPACE_MATCH,
            blocks.keys,
        )
        for ((header, hash) in pinned) {
            assertEquals("block changed: $header", hash, sha256(blocks.getValue(header)))
        }
        assertEquals(
            "service-level helper functions changed",
            "466ff0fb83a123ccb12a0744c125758a2cb7f57adf625cd5e1ae441b2a260eb8",
            sha256(helpers()),
        )
    }

    @Test
    fun `the account space is owner-only, closed over its kinds and stamps updatedAt`() {
        val block = spaceBlock()
        assertTrue(
            "read and delete need the owner and a listed kind",
            Regex("""allow read, delete: if request\.auth != null\s+&& request\.auth\.uid == uid\s+&& isSpaceKind\(\);""")
                .containsMatchIn(block),
        )
        assertTrue(
            "create and update need the owner and a valid doc",
            Regex("""allow create, update: if request\.auth != null\s+&& request\.auth\.uid == uid\s+&& validSpaceDoc\(\);""")
                .containsMatchIn(block),
        )
        assertEquals("only the two allow lines", 2, Regex("""\ballow """).findAll(block).count())
        assertEquals(
            setOf("completions", "drafts", "published", "received", "boards", "replays"),
            quoted(Regex("""return kind in \[(.*?)];""").find(block)!!.groupValues[1]),
        )
        assertTrue(block.contains("d.updatedAt == request.time"))
        assertTrue(block.contains("d.formatVersion == 1"))
    }

    @Test
    fun `each kind allows exactly its codec keys`() {
        val block = spaceBlock()
        assertEquals(
            mapOf(
                "validCompletionsDoc" to setOf("formatVersion", "updatedAt", "records"),
                // Live and tombstone shapes in one list: a merge-set tombstone over a live doc carries both.
                "validDraftDoc" to setOf(
                    "formatVersion", "updatedAt", "gameId", "variant", "draftFormatVersion", "name", "blob",
                    "editedAtMs", "deleted", "deletedAtMs",
                ),
                "validPublishedDoc" to setOf(
                    "formatVersion", "updatedAt", "gameId", "name", "publishedAtMs", "unpublished",
                ),
                "validReceivedDoc" to setOf(
                    "formatVersion", "updatedAt", "gameId", "variant", "sharedFormatVersion", "name", "senderName",
                    "authorUid", "note", "authorTimeMs", "authorHints", "difficulty", "receivedAtMs", "bytes",
                    "postState", "deleted", "deletedAtMs",
                ),
                "validBoardDoc" to setOf(
                    "formatVersion", "updatedAt", "game", "day", "profileKey", "readAtMs", "complete", "rows",
                ),
            ),
            KIND_FUNCTIONS.associateWith { listIn(functionBody(block, it), "hasOnly") },
        )
    }

    @Test
    fun `each kind requires its live shape, drafts and received unless it is a tombstone`() {
        val block = spaceBlock()
        assertEquals(
            mapOf(
                "validDraftDoc" to setOf("gameId", "variant", "draftFormatVersion", "name", "blob", "editedAtMs"),
                "validReceivedDoc" to setOf("gameId", "name", "senderName", "receivedAtMs", "bytes", "postState"),
            ),
            TOMBSTONE_FUNCTIONS.associateWith { listIn(functionBody(block, it), "hasAll") },
        )
        for (function in TOMBSTONE_FUNCTIONS) {
            assertTrue("$function admits a tombstone", functionBody(block, function).contains("isTombstone(d)"))
        }
        assertEquals(setOf("records"), listIn(functionBody(block, "validCompletionsDoc"), "hasAll"))
        assertEquals(
            setOf("gameId", "name", "publishedAtMs", "unpublished"),
            listIn(functionBody(block, "validPublishedDoc"), "hasAll"),
        )
        assertEquals(
            setOf("game", "day", "profileKey", "readAtMs", "complete", "rows"),
            listIn(functionBody(block, "validBoardDoc"), "hasAll"),
        )
    }

    @Test
    fun `the size caps are in the rules`() {
        val block = spaceBlock()
        assertTrue(functionBody(block, "isSpaceName").contains("s.size() <= 64"))
        assertTrue(functionBody(block, "validCompletionsDoc").contains("d.records.size() <= 1000"))
        assertTrue(functionBody(block, "validDraftDoc").contains("d.blob.size() <= 65536"))
        assertTrue(functionBody(block, "validReceivedDoc").contains("d.bytes.size() <= 8192"))
        assertTrue(functionBody(block, "validBoardDoc").contains("d.rows.size() <= 51"))
        for (function in listOf("validDraftDoc", "validPublishedDoc", "validReceivedDoc")) {
            assertTrue("$function caps name", functionBody(block, function).contains("isSpaceName(d.get('name', ''))"))
        }
        assertTrue(functionBody(block, "validReceivedDoc").contains("isSpaceName(d.get('senderName', ''))"))
        for (capped in listOf("isSpaceName(d.get('variant', ''))", "isSpaceName(d.get('difficulty', ''))")) {
            assertTrue(capped, functionBody(block, "validReceivedDoc").contains(capped))
        }
        assertTrue(functionBody(block, "validReceivedDoc").contains("d.authorUid.size() <= 128"))
        assertTrue(functionBody(block, "validReceivedDoc").contains("d.note.size() <= 140"))
    }

    @Test
    fun `replays take no writes until their key list lands`() {
        val block = spaceBlock()
        assertTrue("replays are in the closed list for read and delete", "replays" in kindList(block))
        assertTrue(
            "no write branch names replays",
            !functionBody(block, "validSpaceDoc").contains("replays"),
        )
    }

    private fun topLevelBlocks(): Map<String, String> {
        val blocks = linkedMapOf<String, String>()
        var i = 0
        while (i < rules.size) {
            if (rules[i].startsWith("    match ")) {
                val end = (i until rules.size).first { rules[it] == "    }" }
                blocks[rules[i].trim()] = rules.subList(i, end + 1).joinToString("\n")
                i = end
            }
            i++
        }
        return blocks
    }

    private fun helpers(): String {
        val start = rules.indexOfFirst { it.startsWith("    function ") }
        val end = rules.indexOfLast { it == "  }" }
        return rules.subList(start, end).joinToString("\n")
    }

    private fun spaceBlock(): String = topLevelBlocks()[SPACE_MATCH] ?: error("no account-space block in the rules")

    private fun kindList(block: String): Set<String> =
        quoted(Regex("""return kind in \[(.*?)];""").find(block)!!.groupValues[1])

    /** The text of `function name(...) { ... }` inside the block: from its header to the next `      }` line. */
    private fun functionBody(block: String, name: String): String {
        val lines = block.lines()
        val start = lines.indexOfFirst { it.startsWith("      function $name(") }
        assertTrue("no function $name in the account space", start >= 0)
        val end = (start until lines.size).first { lines[it] == "      }" }
        return lines.subList(start, end + 1).joinToString("\n")
    }

    /** The quoted names in the first `keys().<call>([...])` of [body]. */
    private fun listIn(body: String, call: String): Set<String> =
        quoted(Regex("""keys\(\)\.$call\(\[(.*?)]\)""", RegexOption.DOT_MATCHES_ALL).find(body)!!.groupValues[1])

    private fun quoted(list: String): Set<String> =
        Regex("""'([^']+)'""").findAll(list).map { it.groupValues[1] }.toSet()

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SPACE_MATCH = "match /users/{uid}/{kind}/{docId} {"
        val KIND_FUNCTIONS = listOf(
            "validCompletionsDoc", "validDraftDoc", "validPublishedDoc", "validReceivedDoc", "validBoardDoc",
        )
        val TOMBSTONE_FUNCTIONS = listOf("validDraftDoc", "validReceivedDoc")
    }
}
