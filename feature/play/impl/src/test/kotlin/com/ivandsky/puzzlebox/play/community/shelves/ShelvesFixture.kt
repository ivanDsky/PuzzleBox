package com.ivandsky.puzzlebox.play.community.shelves

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.CommunityFixture
import com.ivandsky.puzzlebox.play.community.NOW
import com.ivandsky.puzzlebox.play.community.NO_EDITOR
import com.ivandsky.puzzlebox.play.community.editorGame
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred

/** A puzzle code that is valid by construction: "P" and the five digits of [n]. */
internal fun codeOf(n: Int) = checkNotNull(ShareCode.parse("P" + n.toString().padStart(5, '0')))

/** The community list held back until [gate] opens, so a test can look at the state while it loads. */
internal class GatedSharedPuzzleRepository(
    private val inner: SharedPuzzleRepository,
    var gate: CompletableDeferred<Unit>? = null,
    val filters: MutableList<CommunityFilter> = mutableListOf(),
) : SharedPuzzleRepository by inner {
    override suspend fun listPublic(filter: CommunityFilter): CommunityListResult {
        filters += filter
        gate?.await()
        return inner.listPublic(filter)
    }
}

/**
 * [ShelvesViewModel] over the real use cases and the fakes: alpha and beta have editors, gamma does not; "me" is
 * signed in, "other" writes the community puzzles, and the clock stands at [NOW].
 */
internal class ShelvesFixture {
    private val community = CommunityFixture()
    val shared = community.shared
    val hidden = community.hidden
    val gated = GatedSharedPuzzleRepository(shared)
    val received = FakeReceivedPuzzleRepository()
    val drafts = FakeDraftRepository()
    val published = FakePublishedPuzzleRepository()
    val completions = FakeCompletionRepository()
    private val identity = FakeIdentityService(Identity(uid = CommunityFixture.ME, displayName = "Me", photoUrl = null))
    private val registry = GameRegistry(listOf(editorGame(ALPHA), editorGame(BETA), TestGames.plugin(NO_EDITOR)))
    private val plays = FakeGames(listOf(ALPHA, BETA)).registry

    suspend fun publish(
        n: Int,
        createdAtMs: Long,
        game: GameId = ALPHA,
        difficulty: Difficulty.Level? = null,
        title: String = "Puzzle $n",
    ) = community.publish(codeOf(n).value, createdAtMs, game, difficulty, title)

    /** [n] puzzles of [game], each a minute older than the one before, from a minute ago. */
    suspend fun publishMany(count: Int, game: GameId = ALPHA, from: Int = 1) {
        repeat(count) { publish(from + it, createdAtMs = NOW - (it + 1) * 60_000L, game = game) }
    }

    /** A puzzle [codeOf]`(n)` the player opened from a friend, so it shows as a friend's. */
    suspend fun receive(n: Int, author: String = "Bo", game: GameId = ALPHA) {
        received.upsert(
            ReceivedPuzzle(
                code = codeOf(n), gameId = game, variant = TestGames.variant(game), sharedFormatVersion = 1,
                puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "Friend $n", authorName = author, note = "",
                authorTimeMs = 100_000, authorHints = 0, authorUid = "friend", openedAt = Instant.ofEpochMilli(NOW),
                unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
            ),
        )
    }

    suspend fun draft(id: String, reservedCode: ShareCode? = null) {
        drafts.upsert(
            Draft(
                id = id, gameId = ALPHA, variant = VariantId("alpha/v1"), draftFormatVersion = 1, blob = byteArrayOf(1),
                title = "Draft $id", updatedAt = Instant.ofEpochMilli(NOW), testSolve = null, reservedCode = reservedCode?.value,
                imported = false, authorName = "Me", note = "",
            ),
        )
    }

    suspend fun myPuzzle(n: Int, unpublished: Boolean = false) {
        published.upsert(
            PublishedPuzzle(
                code = codeOf(n), gameId = ALPHA, variant = VariantId("alpha/v1"), title = "Mine $n", authorTimeMs = 1_000,
                authorHints = 0, createdAt = Instant.ofEpochMilli(NOW), unpublished = unpublished, solvesCount = null, solvesCountAt = null,
            ),
        )
    }

    /** The player solved a puzzle of [game] for the first time. */
    fun solved(game: GameId, day: Int) {
        val record = CompletionRecord(
            TestGames.Ref(game, PuzzleKey.Daily(LocalDate.ofEpochDay(19_000L + day), day)),
            time = 60_000, hints = 0, stats = RecordStats(1, "{}"), isRecord = true,
        )
        playedRecords += record
        completions.setRecords(playedRecords.toList())
    }

    private val playedRecords = mutableListOf<CompletionRecord>()

    fun viewModel() = ShelvesViewModel(
        list = ListCommunityPuzzles(gated, hidden, identity, clock = { NOW }),
        friendPuzzles = ObserveFriendPuzzles(received, FakeSavedPlayRepository(), completions, registry, hidden),
        drafts = drafts,
        published = published,
        completions = completions,
        games = registry,
        plays = plays,
        reportPuzzle = ReportPuzzle(shared, hidden),
        identity = identity,
        idTokens = dismissedGoogleSignIn,
        clock = { NOW },
    )
}
