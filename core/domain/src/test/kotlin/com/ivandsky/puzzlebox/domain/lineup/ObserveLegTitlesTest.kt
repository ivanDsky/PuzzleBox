package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A custom leg's title for an invite: the saved row, else the public puzzle, else nothing (the caller shows the code). */
class ObserveLegTitlesTest {

    private val received = FakeReceivedPuzzleRepository()
    private val published = FakePublishedPuzzleRepository()
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = "cleo" }
    private val titles = ObserveLegTitles(received, published, shared)

    private fun code(value: String) = checkNotNull(ShareCode.parse(value))

    private suspend fun saved(code: String, title: String) = received.upsert(
        ReceivedPuzzle(
            code = code(code), gameId = alpha, variant = TestGames.variant(alpha), sharedFormatVersion = 1,
            puzzleBytes = byteArrayOf(TestGames.MAGIC), title = title, authorName = "Bo", note = "", authorTimeMs = 1_000L,
            authorHints = 0, authorUid = "bo", openedAt = Instant.EPOCH, unpublished = false,
            postState = PostState.Pending, postAttempts = 0, difficulty = null,
        ),
    )

    private suspend fun own(code: String, title: String) = published.upsert(
        PublishedPuzzle(
            code = code(code), gameId = alpha, variant = TestGames.variant(alpha), title = title, authorTimeMs = 1_000L,
            authorHints = 0, createdAt = Instant.EPOCH, unpublished = false, solvesCount = null, solvesCountAt = null,
        ),
    )

    private suspend fun online(code: String, title: String, unpublished: Boolean = false) = shared.publish(
        SharedPuzzleDoc(
            code = code(code), formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = alpha.value, variant = "${alpha.value}/n8",
            shareCodecVersion = 1, payload = byteArrayOf(1), title = title, authorName = "Cleo", note = "",
            authorUid = "cleo", authorTimeMs = 1_000L, authorHints = 0, createdAtMs = 0L, unpublished = false,
            visibility = SharedPuzzleDoc.Visibility.LinkOnly,
        ),
    ).also { if (unpublished) shared.unpublish(code(code)) }

    private suspend fun settled(vararg codes: String) = titles(codes.map(::code)).toList().last()

    @Test
    fun aSavedRowTitlesItsLegWithoutAnOnlineRead() = runTest {
        saved("K7Q2MX", "Crown jewels")
        own("B8C3DF", "Mine")
        shared.online = false

        assertEquals(mapOf(code("K7Q2MX") to "Crown jewels", code("B8C3DF") to "Mine"), settled("K7Q2MX", "B8C3DF"))
    }

    @Test
    fun anUnsavedCodeIsTitledByItsPublicPuzzle() = runTest {
        online("K7Q2MX", "Crown jewels")

        assertEquals(mapOf(code("K7Q2MX") to "Crown jewels"), settled("K7Q2MX"))
    }

    @Test
    fun aCodeNoOneCanReadHasNoTitleSoTheCodeShows() = runTest {
        online("B8C3DF", "Taken down", unpublished = true)

        assertTrue("unknown", settled("K7Q2MX").isEmpty())
        assertTrue("taken down", settled("B8C3DF").isEmpty())
        shared.online = false
        assertTrue("offline", settled("K7Q2MX").isEmpty())
    }

    @Test
    fun theEmptyMapComesFirstThenEachTitleOnceKnown() = runTest {
        saved("K7Q2MX", "Crown jewels")

        val emissions = titles(listOf(code("K7Q2MX"), code("B8C3DF"))).toList()

        assertEquals(emptyMap<ShareCode, String>(), emissions.first())
        assertEquals(mapOf(code("K7Q2MX") to "Crown jewels"), emissions.last())
    }
}
