package com.ivandsky.puzzlebox.play.community.shelves.seeall

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.CommunityFixture
import com.ivandsky.puzzlebox.play.community.NOW
import com.ivandsky.puzzlebox.play.community.NO_EDITOR
import com.ivandsky.puzzlebox.play.community.editorGame
import com.ivandsky.puzzlebox.play.community.shelves.codeOf
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant

/**
 * [SeeAllViewModel] over the real use cases and the fakes: alpha and beta have editors, gamma does not; "me" is signed
 * in, "other" writes the community puzzles, and the clock stands at [NOW].
 */
internal class SeeAllFixture(signedIn: Boolean = true) {
    val shared = FakeSharedPuzzleRepository().apply {
        clock = { NOW }
        signedInUid = CommunityFixture.ME.takeIf { signedIn }
    }
    val hidden = FakeHiddenPuzzles()
    val received = FakeReceivedPuzzleRepository()
    private val identity = FakeIdentityService(Identity(uid = CommunityFixture.ME, displayName = "Me", photoUrl = null).takeIf { signedIn })
    private val registry = GameRegistry(listOf(editorGame(ALPHA), editorGame(BETA), TestGames.plugin(NO_EDITOR)))
    private val plays = FakeGames(listOf(ALPHA, BETA)).registry

    /** Puzzle [n] by [author], published [agoMs] before now. */
    suspend fun publish(
        n: Int,
        agoMs: Long = n * 60_000L,
        game: GameId = ALPHA,
        title: String = "Puzzle $n",
        author: String = "Other",
        difficulty: Difficulty.Level? = null,
        solves: Int? = null,
    ) {
        val before = shared.signedInUid
        shared.signedInUid = CommunityFixture.OTHER
        shared.publishesToday = 0
        shared.publish(
            SharedPuzzleDoc(
                code = codeOf(n), formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = game.value, variant = "${game.value}/v1",
                shareCodecVersion = 1, payload = byteArrayOf(1), title = title, authorName = author, note = "",
                authorUid = CommunityFixture.OTHER, authorTimeMs = 1_000L, authorHints = 0, createdAtMs = NOW - agoMs, unpublished = false,
                visibility = SharedPuzzleDoc.Visibility.Public, difficulty = difficulty, solves = solves,
            ),
        )
        shared.signedInUid = before
    }

    /** Puzzle [n] opened from a friend; [title] and [author] are what the received row says. */
    suspend fun receive(n: Int, title: String = "Friend $n", author: String = "Bo", game: GameId = ALPHA) {
        received.upsert(
            ReceivedPuzzle(
                code = codeOf(n), gameId = game, variant = TestGames.variant(game), sharedFormatVersion = 1,
                puzzleBytes = byteArrayOf(TestGames.MAGIC), title = title, authorName = author, note = "",
                authorTimeMs = 100_000, authorHints = 0, authorUid = "friend", openedAt = Instant.ofEpochMilli(NOW),
                unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
            ),
        )
    }

    fun viewModel() = SeeAllViewModel(
        list = ListCommunityPuzzles(shared, hidden, identity, clock = { NOW }),
        friendPuzzles = ObserveFriendPuzzles(received, FakeSavedPlayRepository(), FakeCompletionRepository(), registry, hidden),
        reportPuzzle = ReportPuzzle(shared, hidden),
        games = registry,
        plays = plays,
        identity = identity,
        idTokens = dismissedGoogleSignIn,
        clock = { NOW },
    )
}
