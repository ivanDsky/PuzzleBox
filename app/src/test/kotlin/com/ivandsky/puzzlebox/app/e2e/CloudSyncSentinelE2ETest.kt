package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.SavedBoardsCloud
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.feed.LinkedInZipDailySource
import com.ivandsky.puzzlebox.network.linkedin.linkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * LinkedIn secrets never reach the cloud (SC12, A30) and Delete saved boards removes boards here and in the cloud
 * (A27), over the real flag-on graph in [CloudSyncWorld] with LinkedIn on. The session store holds [SENTINEL] and the
 * account adapter is the real one, aimed at a local server that keeps every request: a signed-in, connected player
 * reads two days, finishes a game and makes a draft, and the full sync runs. Then every doc the cloud space holds and
 * every payload it was ever asked to write is searched for the session, and every key written is checked against the
 * allow-list `firestore.rules` states for its kind.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class CloudSyncSentinelE2ETest {

    private val wire = LinkedInWire()
    private val world = CloudSyncWorld(linkedIn = true) {
        single<LinkedInSessionCookies> { FakeLinkedInSessionCookies(sentinelSession) }
        single<LinkedInAccountSource> { linkedInAccountSource(wire.url, get()) }
        single<LinkedInPhotoSource> { wire.photoSource() }
    }
    private val me = Identity(uid = "uid-me", displayName = "Me Myself", photoUrl = null)

    @After
    fun closeWorld() {
        world.close()
        wire.close()
    }

    @Test
    fun `the session never reaches a cloud doc and every key written is on the allow-list`() {
        val phone = syncedPhone()
        phone.shell {
            syncEverything(phone)

            val secrets = listOf(SENTINEL, "li_at", sentinelSession.cookieHeader, sentinelSession.csrfToken, "JSESSIONID")
            val everything = world.space.payloads.map { Triple(it.uid, it.kind, it.doc) } +
                KINDS.flatMap { kind -> world.space.docs(me.uid, kind).map { Triple(me.uid, kind, it) } }
            assertTrue("something was written to search", world.space.payloads.isNotEmpty())
            everything.forEach { (uid, kind, doc) ->
                val text = flatten(doc.id) + flatten(doc.fields)
                secrets.forEach { secret -> assertFalse("a $kind doc of $uid holds \"$secret\": ${doc.id}", text.any { secret in it }) }
            }
            assertFalse("the log holds the session", ShadowLog.getLogs().any { SENTINEL in "${it.msg} ${it.throwable?.stackTraceToString()}" })

            // Without these the searches above would also pass for a session that was never used, or a sync that never ran.
            assertTrue("the account requests carried the session", wire.requests.isNotEmpty() && wire.requests.all { SENTINEL in it.getHeader("Cookie").orEmpty() })
            assertEquals("every kind the player has data in was written", KINDS.toSet(), world.space.payloads.map { it.kind }.toSet())

            val allowed = allowedKeys()
            world.space.payloads.forEach { written ->
                assertTrue("only the signed-in account's space is written: $written", written.uid == me.uid)
                val extra = written.doc.fields.keys - allowed.getValue(written.kind)
                assertEquals("keys written to ${written.kind}/${written.doc.id} outside the rules' allow-list", emptySet<String>(), extra)
            }
            boardDocs().forEach { board -> assertBoardDoc(board) }
        }
    }

    @Test
    fun `Delete saved boards removes the boards here and in the cloud and nothing else`() {
        val phone = syncedPhone()
        phone.shell {
            syncEverything(phone)
            val boardIds = world.space.docs(me.uid, "boards").map { it.id }.toSet()
            assertEquals("the two days read are two board docs", 2, boardIds.size)
            assertEquals("both days are shown here", setOf(E2E_TODAY, LINKEDIN_PAST_DAY), phone.shownBoardDays(E2E_TODAY, LINKEDIN_PAST_DAY))
            val others = listOf("completions", "drafts").associateWith { kind -> world.space.docs(me.uid, kind).map { it.id to it.updatedAtMs } }
            val deletesBefore = world.space.deletes
            val writesBefore = world.space.writes

            val result = offMain { graph.koin.get<SavedBoardsCloud>().deleteEverywhere() }

            assertEquals(CloudResult.Ok(Unit), result)
            assertEquals("no board is left in the cloud", emptyList<CloudDoc>(), world.space.docs(me.uid, "boards"))
            assertEquals("one delete per board", deletesBefore + boardIds.size, world.space.deletes)
            assertEquals("no board is shown here", emptySet<Any>(), phone.shownBoardDays(E2E_TODAY, LINKEDIN_PAST_DAY))
            assertEquals("no connection of a deleted board is kept", 0, storedGhostRowCount())
            assertEquals("nothing but boards was deleted", others, KINDS.minus("boards").associateWith { kind -> world.space.docs(me.uid, kind).map { it.id to it.updatedAtMs } })
            assertEquals("the draft is still shown", setOf("draft-1"), phone.shownDrafts().map { it.id }.toSet())

            // Deleted boards are not pending: a later run uploads nothing.
            val reads = world.space.reads
            offMain { phone.cloud.syncNow() }
            awaitCloud("a run after the delete") { world.space.reads > reads && phone.synced() }
            assertEquals("a run after the delete writes nothing", writesBefore, world.space.writes)
            assertEquals("and the cloud has no board", emptyList<CloudDoc>(), world.space.docs(me.uid, "boards"))
        }
    }

    /** A phone that is signed in and has what a full sync uploads, but for the reads, which need the shell. */
    private fun syncedPhone(): CloudSyncWorld.Device {
        val phone = world.device()
        val edition = LinkedInZipDailySource.EDITION
        phone.graph.daily.setLinkedInGameType(LINKEDIN_GAME, edition.gameTypeId)
        phone.graph.daily.setPuzzleNumbers(LINKEDIN_GAME, edition::numberOrNull)
        phone.graph.daily.setArchiveRange(LINKEDIN_GAME, LINKEDIN_PAST_DAY.minusDays(1)..E2E_TODAY)
        phone.signIn(me)
        return phone
    }

    /** Connects, finishes a game, makes a draft, reads two days, and waits until the cloud holds all of it. */
    private fun ShellRobot.syncEverything(phone: CloudSyncWorld.Device) {
        graph.connectLinkedIn()
        phone.finish(E2E_TODAY.minusDays(5), puzzleNumber = 580)
        phone.makeDraft("draft-1", "Mine")
        openLinkedInGamePage()
        // One day at a time: picking another day while a read is held back drops that read.
        awaitLinkedIn { E2E_TODAY in phone.shownBoardDays(E2E_TODAY) }
        pickLinkedInPastDay()
        awaitLinkedIn { phone.shownBoardDays(E2E_TODAY, LINKEDIN_PAST_DAY) == setOf(E2E_TODAY, LINKEDIN_PAST_DAY) }
        awaitLinkedIn {
            KINDS.all { world.space.docs(me.uid, it).isNotEmpty() } && world.space.docs(me.uid, "boards").size == 2 &&
                phone.uploaded(me.uid) && phone.synced()
        }
    }

    private fun boardDocs(): List<CloudDoc> = world.space.payloads.filter { it.kind == "boards" }.map { it.doc }

    /** A board doc says only what the board says: its profile as a hash, its connections by name, time, flags and member id. */
    private fun assertBoardDoc(board: CloudDoc) {
        val key = sha256Hex(LINKEDIN_MEMBER).take(PROFILE_KEY_LENGTH)
        assertEquals("the profile is its hash", key, board.fields["profileKey"])
        assertTrue("the doc id is game, day and profile hash: ${board.id}", Regex("zip_\\d{4}-\\d{2}-\\d{2}_$key").matches(board.id))
        val rows = board.fields["rows"] as List<*>
        assertTrue("the board has the connections of the day", rows.isNotEmpty())
        rows.forEach { raw ->
            val row = raw as Map<*, *>
            val extra = row.keys - ROW_KEYS
            assertEquals("keys of a board row outside the allow-list", emptySet<Any?>(), extra)
            assertFalse("the own row carries a ref", row["isOwn"] == true && "ref" in row.keys)
        }
        val text = flatten(board.fields).joinToString("\n")
        assertFalse("the member id is in a board doc", LINKEDIN_MEMBER in text)
        assertFalse("a photo address is in a board doc", "licdn" in text || "http" in text)
    }

    /** The keys `firestore.rules` lets each kind's doc carry (its `hasOnly` list), less the `updatedAt` the server stamps. */
    private fun allowedKeys(): Map<String, Set<String>> {
        val rules = File("../backend/firebase/firestore.rules").readText()
        return RULE_FUNCTIONS.mapValues { (_, function) ->
            val list = Regex("function $function\\(d\\) \\{\\s*return d\\.keys\\(\\)\\.hasOnly\\(\\[([^\\]]*)]")
                .find(rules)?.groupValues?.get(1) ?: error("firestore.rules has no $function with a hasOnly list")
            Regex("'([^']+)'").findAll(list).map { it.groupValues[1] }.toSet() - "updatedAt"
        }.also { allowed -> assertEquals("every kind written has a list", KINDS.toSet(), allowed.keys) }
    }

    /** Runs [block] on another thread while this one keeps turning the main looper, which a day's collectors may need. */
    private fun <T> ShellRobot.offMain(block: suspend () -> T): T {
        val done = CompletableFuture.supplyAsync { runBlocking { block() } }
        awaitLinkedIn { done.isDone }
        return done.get()
    }

    private fun ShellRobot.storedGhostRowCount(): Int = runBlocking {
        listOf(E2E_TODAY, LINKEDIN_PAST_DAY).sumOf { day -> linkedInDb().ghostDao().observe(ZipDefinition.id.value, day.toString()).first().size }
    }

    /** Every key and every value of [value] as text, one entry each; bytes as one char per byte. */
    private fun flatten(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is ByteArray -> listOf(value.toString(Charsets.ISO_8859_1))
        is Map<*, *> -> value.flatMap { (key, nested) -> flatten(key) + flatten(nested) }
        is List<*> -> value.flatMap(::flatten)
        else -> listOf(value.toString())
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PROFILE_KEY_LENGTH = 16
        val KINDS = listOf("completions", "drafts", "boards")
        val RULE_FUNCTIONS = mapOf("completions" to "validCompletionsDoc", "drafts" to "validDraftDoc", "boards" to "validBoardDoc")
        val ROW_KEYS = setOf("name", "timeMs", "hintFree", "flawless", "isOwn", "ref")
    }
}
