package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository

/** The real fake behind a recorder: [reads] lists every [fetch], and [fetchAnswer] scripts one (a read that never answers). */
internal class ReadCountingSharedPuzzleRepository(
    private val fake: FakeSharedPuzzleRepository = FakeSharedPuzzleRepository(),
) : SharedPuzzleRepository by fake {
    val reads = mutableListOf<ShareCode>()
    var fetchAnswer: suspend (ShareCode) -> SharedFetchResult = { fake.fetch(it) }

    var online: Boolean
        get() = fake.online
        set(value) { fake.online = value }

    override suspend fun fetch(code: ShareCode): SharedFetchResult {
        reads += code
        return fetchAnswer(code)
    }

    /** Stores a published puzzle by [code]; [unpublished] stores one its author took down. */
    suspend fun store(code: ShareCode, unpublished: Boolean = false) {
        fake.signedInUid = AUTHOR
        fake.publish(
            SharedPuzzleDoc(
                code = code, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = "alpha", variant = "alpha/v",
                shareCodecVersion = 1, payload = byteArrayOf(1), title = "T", authorName = "Bo", note = "",
                authorUid = AUTHOR, authorTimeMs = 1_000L, authorHints = 0, createdAtMs = 0L, unpublished = false,
            ),
        )
        if (unpublished) fake.unpublish(code)
    }

    private companion object {
        const val AUTHOR = "bo"
    }
}
