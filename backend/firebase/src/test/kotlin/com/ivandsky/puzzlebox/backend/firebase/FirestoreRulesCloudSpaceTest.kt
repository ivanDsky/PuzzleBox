package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.model.SolveReplay
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
 *
 * Two pins moved after that: the daily entries block when it gained its closed key set, and the helpers when
 * the entry window moved there for the daily replays block to share. The visibility and daily replays blocks
 * are pinned against their codecs rather than by hash, so a doc key or a tier cannot change on one side only.
 */
class FirestoreRulesCloudSpaceTest {

    private val rules = File("firestore.rules").readText().lines()

    @Test
    fun `the hash-pinned blocks, deny-all and helpers are unchanged`() {
        val pinned = mapOf(
            "match /{document=**} {" to "2fa7810103dc8eb3f4050c94653a46e8c3ed08cf3536292a3963424eadffd1e9",
            "match /leaderboards/{boardId}/entries/{uid} {" to
                "7a11c5d570ecd206ef9e3314e6f5692c6dabb361d25527fda5f18a5b1b0784a0",
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
            "the rules hold exactly the pinned blocks plus visibility, daily replays and the account space",
            pinned.keys + setOf(VISIBILITY_MATCH, DAILY_REPLAYS_MATCH, SPACE_MATCH),
            blocks.keys,
        )
        for ((header, hash) in pinned) {
            assertEquals("block changed: $header", hash, sha256(blocks.getValue(header)))
        }
        assertEquals(
            "service-level helper functions changed",
            "7277ef8acb16bd7fc6e416720ce7b0cde8adbba3b56fab35112f5f9e613f3871",
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
                "validReplayDoc" to setOf("formatVersion", "updatedAt", "replay"),
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
        assertEquals(setOf("replay"), listIn(functionBody(block, "validReplayDoc"), "hasAll"))
    }

    @Test
    fun `the size caps are in the rules`() {
        val block = spaceBlock()
        assertTrue(functionBody(block, "isSpaceName").contains("s.size() <= 64"))
        assertTrue(functionBody(block, "validCompletionsDoc").contains("d.records.size() <= 1000"))
        assertTrue(functionBody(block, "validDraftDoc").contains("d.blob.size() <= 65536"))
        assertTrue(functionBody(block, "validReceivedDoc").contains("d.bytes.size() <= 8192"))
        assertTrue(functionBody(block, "validBoardDoc").contains("d.rows.size() <= 51"))
        assertTrue(functionBody(block, "validReplayDoc").contains("d.replay.size() <= 12288"))
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
    fun `replays are written under a sync id, as bytes of one to 12288`() {
        val block = spaceBlock()
        assertTrue("replays are in the closed list", "replays" in kindList(block))
        assertTrue(
            "the write branch validates replays",
            functionBody(block, "validSpaceDoc").contains("kind == 'replays' && validReplayDoc(d)"),
        )
        val body = functionBody(block, "validReplayDoc")
        assertTrue("one doc per sync id", body.contains("docId.matches('^[0-9a-f]{32}$')"))
        assertTrue("the replay is bytes", body.contains("d.replay is bytes"))
        assertTrue("the cap is the duel replay's", body.contains("d.replay.size() <= 12288"))
        assertTrue("an empty replay is refused", body.contains("d.replay.size() > 0"))
    }

    @Test
    fun `a daily entry carries only the codec's keys, with capped strings`() {
        val body = functionBody(block(ENTRIES_MATCH), "validEntry")
        val entry = LeaderboardEntry("bob", "Bob", "zip/2026-11-07/1", timeMs = 61_000, hints = 0, completedAtMs = 1)
        assertEquals(LeaderboardDocCodec.encode(entry).keys, listIn(body, "hasOnly"))
        assertTrue("the name is capped", body.contains("entry.displayName.size() <= 64"))
        assertTrue("the ref is capped", body.contains("ref.size() <= 64"))
    }

    @Test
    fun `a tier is read by id when signed in, set by its owner over the ladder and never deleted`() {
        val block = block(VISIBILITY_MATCH)
        assertTrue("any signed-in player gets a tier", block.contains("allow get: if request.auth != null;"))
        assertTrue("no listing, no delete", block.contains("allow list, delete: if false;"))
        assertTrue(
            "create and update need the owner and a valid tier doc",
            Regex("""allow create, update: if request\.auth != null\s+&& request\.auth\.uid == uid\s+&& validTier\(\);""")
                .containsMatchIn(block),
        )
        assertEquals("only the three allow lines", 3, Regex("""\ballow """).findAll(block).count())
        val body = functionBody(block, "validTier")
        assertEquals(encodeTierDoc(VisibilityTier.Board).keys, listIn(body, "hasOnly"))
        assertEquals(
            VisibilityTier.entries.map { it.storageName }.toSet(),
            quoted(Regex("""d\.tier in \[(.*?)]""").find(body)!!.groupValues[1]),
        )
        assertTrue("the doc version is the codec's", body.contains("d.v == $TIER_DOC_VERSION;"))
    }

    @Test
    fun `a daily replay is read while its owner's tier is replays, written once in the entry window, deleted by its owner`() {
        val block = block(DAILY_REPLAYS_MATCH)
        val tier = """/databases/\$\(database\)/documents/visibility/\$\(uid\)"""
        assertTrue(
            "a get needs the owner's tier doc to exist and say replays",
            Regex(
                """allow get: if request\.auth != null\s+&& exists\($tier\)\s+""" +
                    """&& get\($tier\)\.data\.tier == '${VisibilityTier.Replays.storageName}';""",
            ).containsMatchIn(block),
        )
        assertTrue("no listing, no rewrite", block.contains("allow list, update: if false;"))
        assertTrue(
            "create needs the owner and a valid replay doc",
            Regex("""allow create: if request\.auth != null\s+&& request\.auth\.uid == uid\s+&& validDailyReplay\(\);""")
                .containsMatchIn(block),
        )
        assertTrue(
            "the owner takes it down",
            block.contains("allow delete: if request.auth != null && request.auth.uid == uid;"),
        )
        assertEquals("only the four allow lines", 4, Regex("""\ballow """).findAll(block).count())
        val body = functionBody(block, "validDailyReplay")
        assertEquals(setOf("uid", "blob", "formatVersion", "createdAtMs"), listIn(body, "hasOnly"))
        assertTrue("the replay is bytes", body.contains("d.blob is bytes"))
        assertTrue("the cap is a solve replay's", body.contains("d.blob.size() <= ${SolveReplay.MAX_BYTES}"))
        assertTrue("a daily game's board", body.contains("board[0] in dailyGameIds()"))
        assertTrue("a day in the entry window", body.contains("board[1] in dailyWindowDays()"))
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

    private fun spaceBlock(): String = block(SPACE_MATCH)

    private fun block(header: String): String = topLevelBlocks()[header] ?: error("no `$header` block in the rules")

    private fun kindList(block: String): Set<String> =
        quoted(Regex("""return kind in \[(.*?)];""").find(block)!!.groupValues[1])

    /** The text of `function name(...) { ... }` inside the block: from its header to the next `      }` line. */
    private fun functionBody(block: String, name: String): String {
        val lines = block.lines()
        val start = lines.indexOfFirst { it.startsWith("      function $name(") }
        assertTrue("no function $name in the block", start >= 0)
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
        const val ENTRIES_MATCH = "match /leaderboards/{boardId}/entries/{uid} {"
        const val DAILY_REPLAYS_MATCH = "match /leaderboards/{boardId}/replays/{uid} {"
        const val VISIBILITY_MATCH = "match /visibility/{uid} {"
        const val SPACE_MATCH = "match /users/{uid}/{kind}/{docId} {"
        val KIND_FUNCTIONS = listOf(
            "validCompletionsDoc", "validDraftDoc", "validPublishedDoc", "validReceivedDoc", "validBoardDoc", "validReplayDoc",
        )
        val TOMBSTONE_FUNCTIONS = listOf("validDraftDoc", "validReceivedDoc")
    }
}
