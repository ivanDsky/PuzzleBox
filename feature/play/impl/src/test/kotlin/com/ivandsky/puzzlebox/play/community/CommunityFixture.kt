package com.ivandsky.puzzlebox.play.community

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.AlphaShareEditor
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository

internal const val NOW = 1_800_000_000_000L
internal const val MINUTE = 60_000L
internal const val HOUR = 60 * MINUTE

internal val ALPHA = TestGames.alpha
internal val BETA = TestGames.beta

/** A game with no editor: nobody can make a puzzle of it, so it has no chip and no row. */
internal val NO_EDITOR = GameId("gamma")

internal fun code(value: String) = checkNotNull(ShareCode.parse(value))

/** [TestGames.plugin] for [id] with the alpha share editor, which is all "has an editor" asks. */
internal fun editorGame(id: GameId): GamePlugin<Unit, TestGames.Session, TestGames.Model> {
    val base = TestGames.plugin(id)
    return GamePlugin(
        definition = base.definition,
        rules = base.rules,
        session = base.session,
        score = base.score,
        stateOf = base.stateOf,
        initialSession = base.initialSession,
        refOf = base.refOf,
        playAgainSession = base.playAgainSession,
        rendererModel = base.rendererModel,
        editor = AlphaShareEditor,
    )
}

/** The community list's repository, noting every filter it was asked for. */
internal class RecordingSharedPuzzleRepository(private val inner: FakeSharedPuzzleRepository) : SharedPuzzleRepository by inner {
    val filters = mutableListOf<CommunityFilter>()

    override suspend fun listPublic(filter: CommunityFilter): CommunityListResult {
        filters += filter
        return inner.listPublic(filter)
    }
}

/**
 * [CommunityViewModel] over the real use cases and the fake repository: alpha and beta have editors, gamma does not;
 * "me" is signed in, "other" writes the puzzles, and the clock stands at [NOW].
 */
internal class CommunityFixture(signedIn: Boolean = true) {
    val shared = FakeSharedPuzzleRepository().apply {
        clock = { NOW }
        signedInUid = ME.takeIf { signedIn }
    }
    val hidden = FakeHiddenPuzzles()
    val recording = RecordingSharedPuzzleRepository(shared)
    private val identity = FakeIdentityService(Identity(uid = ME, displayName = "Me", photoUrl = null).takeIf { signedIn })
    private val fakeGames = FakeGames(listOf(ALPHA, BETA))
    private val games = GameRegistry(listOf(editorGame(ALPHA), editorGame(BETA), TestGames.plugin(NO_EDITOR)))

    /** Published as another player, so it lists for "me" and can be reported. */
    suspend fun publish(
        code: String,
        createdAtMs: Long,
        game: GameId = ALPHA,
        difficulty: Difficulty.Level? = null,
        title: String = "Puzzle $code",
    ) {
        val before = shared.signedInUid
        shared.signedInUid = OTHER
        shared.publishesToday = 0
        shared.publish(
            SharedPuzzleDoc(
                code = code(code), formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = game.value, variant = "${game.value}/v1",
                shareCodecVersion = 1, payload = byteArrayOf(1), title = title, authorName = "Other", note = "",
                authorUid = OTHER, authorTimeMs = 1_000L, authorHints = 0, createdAtMs = createdAtMs, unpublished = false,
                visibility = SharedPuzzleDoc.Visibility.Public, difficulty = difficulty,
            ),
        )
        shared.signedInUid = before
    }

    fun viewModel() = CommunityViewModel(
        list = ListCommunityPuzzles(recording, hidden, identity, clock = { NOW }),
        reportPuzzle = ReportPuzzle(recording, hidden),
        games = games,
        plays = fakeGames.registry,
        identity = identity,
        idTokens = dismissedGoogleSignIn,
        clock = { NOW },
    )

    companion object {
        const val ME = "me"
        const val OTHER = "other"
    }
}
