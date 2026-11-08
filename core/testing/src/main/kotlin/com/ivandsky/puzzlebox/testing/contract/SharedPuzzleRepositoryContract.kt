package com.ivandsky.puzzlebox.testing.contract

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UnpublishResult
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behavior every [SharedPuzzleRepository] adapter must pass. Subclasses supply a fresh, empty,
 * online [repository] and the two hooks below; adapter-specific cases live in the subclass.
 * Test names are camel case because this class ships in a main source set that gets dexed.
 */
abstract class SharedPuzzleRepositoryContract {

    protected abstract val repository: SharedPuzzleRepository

    /** Makes [uid] the signed-in user for the calls that follow. */
    protected abstract fun signInAs(uid: String)

    /** Writes [doc] to the backing store as-is, the way another app version would. */
    protected abstract suspend fun seed(doc: SharedPuzzleDoc)

    protected fun code(value: String = CODE): ShareCode = checkNotNull(ShareCode.parse(value))

    protected fun doc(
        code: ShareCode = code(),
        authorUid: String = AUTHOR,
        title: String = "Corner queens",
        formatVersion: Int = SharedPuzzleDoc.FORMAT_VERSION,
        unpublished: Boolean = false,
    ) = SharedPuzzleDoc(
        code = code,
        formatVersion = formatVersion,
        gameId = "queens",
        variant = "classic",
        shareCodecVersion = 1,
        payload = byteArrayOf(1, 2, 3, 4),
        title = title,
        authorName = "Ivan",
        note = "Start in the corner",
        authorUid = authorUid,
        authorTimeMs = 102_000L,
        authorHints = 1,
        createdAtMs = 1_700_000_000_000L,
        unpublished = unpublished,
    )

    @Test
    fun publishCreatesTheDocThatFetchReturns() = runTest {
        signInAs(AUTHOR)

        assertEquals(PublishResult.Published(code()), repository.publish(doc()))
        assertEquals(SharedFetchResult.Found(doc()), repository.fetch(code()))
    }

    @Test
    fun fetchOfAnAbsentCodeIsNotFound() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())

        assertEquals(SharedFetchResult.NotFound, repository.fetch(code("ZZZ999")))
    }

    @Test
    fun republishingTheSameContentAsTheSameAuthorIsPublishedAndKeepsTheFirstDoc() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())

        val retry = doc().copy(createdAtMs = doc().createdAtMs + 1)
        assertEquals(PublishResult.Published(code()), repository.publish(retry))
        assertEquals(SharedFetchResult.Found(doc()), repository.fetch(code()))
    }

    @Test
    fun republishingDifferentContentAsTheSameAuthorIsCodeTakenAndKeepsTheFirstDoc() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())

        assertEquals(PublishResult.CodeTaken, repository.publish(doc(title = "Retry")))
        assertEquals(SharedFetchResult.Found(doc()), repository.fetch(code()))
    }

    @Test
    fun anotherAuthorsCodeIsTakenAndLeftUntouched() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())

        signInAs(OTHER)
        assertEquals(PublishResult.CodeTaken, repository.publish(doc(authorUid = OTHER, title = "Mine")))
        assertEquals(SharedFetchResult.Found(doc()), repository.fetch(code()))
    }

    @Test
    fun unpublishOnlyFlipsTheFlagAndFetchStillFindsIt() = runTest {
        signInAs(AUTHOR)
        repository.publish(doc())

        assertEquals(UnpublishResult.Done, repository.unpublish(code()))
        assertEquals(SharedFetchResult.Found(doc(unpublished = true)), repository.fetch(code()))
    }

    @Test
    fun aDocAboveFormatVersionOneIsUnsupported() = runTest {
        seed(doc(formatVersion = SharedPuzzleDoc.FORMAT_VERSION + 1))

        assertEquals(SharedFetchResult.UnsupportedVersion(SharedPuzzleDoc.FORMAT_VERSION + 1), repository.fetch(code()))
    }

    protected companion object {
        const val CODE: String = "K7Q2MX"
        const val AUTHOR: String = "author-uid"
        const val OTHER: String = "other-uid"
    }
}
