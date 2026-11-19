package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.COMMUNITY_LIST_LIMIT
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.ShareCode
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
 *
 * The puzzles pin moved with the ADR-0020 amendment (the community list rule and the two optional fields), and
 * the puzzle reports block is pinned beside it. The puzzle's key and word lists are also checked against its
 * codec.
 */
class FirestoreRulesCloudSpaceTest {

    private val rules = File("firestore.rules").readText().lines()

    @Test
    fun `the hash-pinned blocks, deny-all and helpers are unchanged`() {
        val pinned = mapOf(
            "match /{document=**} {" to "2fa7810103dc8eb3f4050c94653a46e8c3ed08cf3536292a3963424eadffd1e9",
            "match /leaderboards/{boardId}/entries/{uid} {" to
                "7a11c5d570ecd206ef9e3314e6f5692c6dabb361d25527fda5f18a5b1b0784a0",
            PUZZLES_MATCH to "9dfd86eb8809213fc5373864ae2a1739d9540ed33c05026f41fdd307763e5dfb",
            "match /puzzles/{code}/entries/{uid} {" to
                "09382f2ceb2f5fcaccd2b22f12d2e501898ec1715275abe390fbfd81ee7f60cb",
            "match /puzzles/{code}/reports/{uid} {" to
                "5f91335ff9213308938930f62c47f7332e4ef8b05201c92995c9069a67dab031",
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
            "read and delete need the owner, a Google sign-in and a listed kind",
            Regex(
                """allow read, delete: if request\.auth != null\s+&& request\.auth\.uid == uid\s+""" +
                    """&& isGoogleSignIn\(\)\s+&& isSpaceKind\(\);""",
            ).containsMatchIn(block),
        )
        assertTrue(
            "create and update need the owner, a Google sign-in and a valid doc",
            Regex(
                """allow create, update: if request\.auth != null\s+&& request\.auth\.uid == uid\s+""" +
                    """&& isGoogleSignIn\(\)\s+&& validSpaceDoc\(\);""",
            ).containsMatchIn(block),
        )
        assertEquals("only the two allow lines", 2, Regex("""\ballow """).findAll(block).count())
        assertEquals(
            setOf("completions", "drafts", "published", "received", "boards", "replays", "reveals", "trainer"),
            quoted(Regex("""return kind in \[(.*?)];""").find(block)!!.groupValues[1]),
        )
        assertTrue(block.contains("d.updatedAt == request.time"))
        assertTrue(block.contains("d.formatVersion == 1"))
    }

    @Test
    fun `only a Google sign-in reaches the space`() {
        val body = functionBody(spaceBlock(), "isGoogleSignIn")
        assertTrue(
            "the provider claim is google.com",
            body.contains("request.auth.token.firebase.sign_in_provider == 'google.com'"),
        )
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
                    "editedAtMs", "reservedCode", "deleted", "deletedAtMs",
                ),
                "validPublishedDoc" to setOf(
                    "formatVersion", "updatedAt", "gameId", "name", "publishedAtMs", "unpublished", "variant",
                    "authorTimeMs",
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
                "validRevealDoc" to setOf("formatVersion", "updatedAt", "seenAtMs", "myScore", "theirScore"),
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
        assertEquals(setOf("seenAtMs"), listIn(functionBody(block, "validRevealDoc"), "hasAll"))
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
        assertTrue(functionBody(block, "validPublishedDoc").contains("d.variant.size() <= 32"))
        assertTrue(functionBody(block, "validPublishedDoc").contains("d.authorTimeMs >= 0"))
        val board = functionBody(block, "validBoardDoc")
        assertTrue("the game is capped", board.contains("d.game.size() <= 32"))
        assertTrue("the day is a date's length", board.contains("d.day.size() == 10"))
        assertTrue("the profile key is its hash's length", board.contains("d.profileKey.size() == 16"))
        assertTrue(
            "completions keys are sync ids",
            functionBody(block, "validCompletionsDoc").contains("d.records.keys().join('').matches('^([0-9a-f]{32})*$')"),
        )
    }

    @Test
    fun `the new optional fields are share-code or bounded and never required`() {
        val block = spaceBlock()
        assertTrue(functionBody(block, "validDraftDoc").contains("(!('reservedCode' in d) || isShareCode(d.reservedCode))"))
        for (field in listOf("variant", "authorTimeMs")) {
            assertTrue("$field is optional", functionBody(block, "validPublishedDoc").contains("!('$field' in d)"))
        }
        assertEquals(setOf("gameId", "name", "publishedAtMs", "unpublished"), listIn(functionBody(block, "validPublishedDoc"), "hasAll"))
        assertEquals(
            setOf("gameId", "variant", "draftFormatVersion", "name", "blob", "editedAtMs"),
            listIn(functionBody(block, "validDraftDoc"), "hasAll"),
        )
    }

    @Test
    fun `every kind names its doc ids`() {
        val block = spaceBlock()
        val patterns = mapOf(
            "validCompletionsDoc" to "docId.matches('^[0-9]{4}-(0[1-9]|1[0-2])(-[0-9]+)?\$')",
            "validDraftDoc" to "docId.matches('^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\$')",
            "validPublishedDoc" to "isShareCode(docId)",
            "validReceivedDoc" to "isShareCode(docId)",
            "validBoardDoc" to "docId.matches('^[a-z0-9]{1,32}_[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9a-f]{16}\$')",
            "validReplayDoc" to "docId.matches('^[0-9a-f]{32}\$')",
            "validRevealDoc" to "isShareCode(docId)",
        )
        assertEquals(KIND_FUNCTIONS.toSet(), patterns.keys)
        for ((function, pattern) in patterns) {
            assertTrue("$function pins its doc id", functionBody(block, function).contains(pattern))
        }
        assertTrue(
            "a board doc is named by its own game, day and profile key",
            functionBody(block, "validBoardDoc").contains("docId == d.game + '_' + d.day + '_' + d.profileKey"),
        )
    }

    @Test
    fun `a board row carries only the codec's row keys, for every row the doc may hold`() {
        val rows = functionBody(spaceBlock(), "validBoardRows")
        assertEquals(
            setOf("name", "timeMs", "hintFree", "flawless", "isOwn", "ref"),
            quoted(Regex("""let k = \[(.*?)];""").find(rows)!!.groupValues[1]),
        )
        assertEquals(
            "one key check per possible row (rows cap 51)",
            (0..50).toList(),
            Regex("""rows\[(\d+)]\.keys\(\)\.hasOnly\(k\)""").findAll(rows).map { it.groupValues[1].toInt() }.toList(),
        )
        assertTrue(functionBody(spaceBlock(), "validBoardDoc").contains("validBoardRows(d.rows)"))
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
    fun `a reveal is written once under its share code, with scores of at most ten legs`() {
        val block = spaceBlock()
        assertTrue("reveals are in the closed list", "reveals" in kindList(block))
        assertTrue(functionBody(block, "validSpaceDoc").contains("kind == 'reveals' && validRevealDoc(d)"))
        val body = functionBody(block, "validRevealDoc")
        assertTrue("the seen time is a non-negative int", body.contains("d.seenAtMs is int") && body.contains("d.seenAtMs >= 0"))
        for (score in listOf("myScore", "theirScore")) {
            assertTrue("$score is optional and bounded", body.contains("(!('$score' in d) || isRevealScore(d.$score))"))
            assertTrue("$score never changes", body.contains("d.get('$score', null) == resource.data.get('$score', null)"))
        }
        assertTrue("a new doc or an identical re-put", body.contains("(request.method == 'create'\n            || (d.seenAtMs"))
        assertTrue("the seen time never changes", body.contains("d.seenAtMs == resource.data.seenAtMs"))
        assertTrue("a score is at most the duel's legs", functionBody(block, "isRevealScore").contains("s <= ${Match.MAX_LEGS}"))
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

    @Test
    fun `a puzzle requires the keys of a link-only doc and admits the codec's community fields in the codec's words`() {
        val body = functionBody(block(PUZZLES_MATCH), "validPuzzle")
        val linkOnly = SharedPuzzleDocCodec.encode(puzzle())
        val public = SharedPuzzleDocCodec.encode(puzzle(SharedPuzzleDoc.Visibility.Public, Difficulty.Level.Easy))
        assertEquals(
            "every key of a link-only doc is required",
            linkOnly.keys,
            quoted(Regex("""let fields = \[(.*?)];""", RegexOption.DOT_MATCHES_ALL).find(body)!!.groupValues[1]),
        )
        assertTrue("the required keys are the base of both checks", body.contains("puzzle.keys().hasAll(fields)"))
        assertEquals(
            "the optional keys are the ones a public doc adds",
            public.keys - linkOnly.keys,
            quoted(Regex("""puzzle\.keys\(\)\.hasOnly\(fields\.concat\(\[(.*?)]\)\)""").find(body)!!.groupValues[1]),
        )
        assertEquals(
            "public is the codec's word; link is admitted for a client that writes it",
            setOf(public.getValue(SharedPuzzleDocCodec.FIELD_VISIBILITY), "link"),
            quoted(Regex("""puzzle\.visibility in \[(.*?)]""").find(body)!!.groupValues[1]),
        )
        assertEquals(
            Difficulty.Level.entries
                .map { SharedPuzzleDocCodec.encode(puzzle(difficulty = it)).getValue(SharedPuzzleDocCodec.FIELD_DIFFICULTY) }
                .toSet(),
            quoted(Regex("""puzzle\.difficulty in \[(.*?)]""").find(body)!!.groupValues[1]),
        )
    }

    @Test
    fun `puzzles are listed only by a query for public, published ones with a limit of at most the client's`() {
        val block = block(PUZZLES_MATCH)
        val public = SharedPuzzleDocCodec.encode(puzzle(SharedPuzzleDoc.Visibility.Public))
        assertTrue(
            "the one list rule names the codec's field and word",
            Regex(
                """allow list: if resource\.data\.${SharedPuzzleDocCodec.FIELD_VISIBILITY} == """ +
                    """'${public.getValue(SharedPuzzleDocCodec.FIELD_VISIBILITY)}'\s+""" +
                    """&& resource\.data\.${SharedPuzzleDocCodec.FIELD_UNPUBLISHED} == false\s+""" +
                    """&& request\.query\.limit <= $COMMUNITY_LIST_LIMIT;""",
            ).containsMatchIn(block),
        )
        assertEquals("one list rule", 1, Regex("""\ballow [a-z, ]*\blist\b""").findAll(block).count())
    }

    @Test
    fun `a report requires exactly the keys and the version the client writes`() {
        val body = functionBody(block("match /puzzles/{code}/reports/{uid} {"), "validReport")
        val report = reportFields("reporter-uid")

        assertEquals(report.keys, quoted(Regex("""let fields = \[(.*?)];""").find(body)!!.groupValues[1]))
        assertTrue(body.contains("report.formatVersion == ${report["formatVersion"]}\n"))
    }

    private fun puzzle(
        visibility: SharedPuzzleDoc.Visibility = SharedPuzzleDoc.Visibility.LinkOnly,
        difficulty: Difficulty.Level? = null,
    ) = SharedPuzzleDoc(
        code = checkNotNull(ShareCode.parse("K7Q2MX")),
        formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
        gameId = "queens",
        variant = "classic",
        shareCodecVersion = 1,
        payload = byteArrayOf(1, 2, 3, 4),
        title = "Corner queens",
        authorName = "Ivan",
        note = "",
        authorUid = "author-uid",
        authorTimeMs = 61_000,
        authorHints = 0,
        createdAtMs = 1_762_000_000_000,
        unpublished = false,
        visibility = visibility,
        difficulty = difficulty,
    )

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
        const val PUZZLES_MATCH = "match /puzzles/{code} {"
        const val VISIBILITY_MATCH = "match /visibility/{uid} {"
        const val SPACE_MATCH = "match /users/{uid}/{kind}/{docId} {"
        val KIND_FUNCTIONS = listOf(
            "validCompletionsDoc", "validDraftDoc", "validPublishedDoc", "validReceivedDoc", "validBoardDoc", "validReplayDoc",
            "validRevealDoc",
        )
        val TOMBSTONE_FUNCTIONS = listOf("validDraftDoc", "validReceivedDoc")
    }
}
