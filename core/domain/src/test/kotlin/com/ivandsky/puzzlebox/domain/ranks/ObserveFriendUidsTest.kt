package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Friends are the duel opponents; the viewer is never their own friend and puzzle authors do not count. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFriendUidsTest {

    private val identity = FakeIdentityService(Identity(ME.uid, ME.name, null))
    private val repo = FakeMatchRepository().apply { signedInUid = ME.uid }
    private val friends = ObserveFriendUids(repo, identity)

    private suspend fun duel(code: String, challenger: Player, opponent: Player? = null, author: String? = null) {
        val asUid = repo.signedInUid
        repo.signedInUid = challenger.uid
        val shareCode = checkNotNull(ShareCode.parse(code))
        repo.create(
            MatchDraft(shareCode, 24, challenger, null, listOf(MatchLeg(LegRef.Daily(GameId("zip"), DAY, 1), author)), true, null),
        )
        if (opponent != null) {
            repo.signedInUid = opponent.uid
            repo.claim(shareCode, opponent)
        }
        repo.signedInUid = asUid
    }

    @Test
    fun `opponents of my duels without me`() = runTest {
        duel("AAAAA2", ME, BO)
        duel("AAAAA3", CAT, ME)

        assertEquals(setOf(BO.uid, CAT.uid), friends().first())
    }

    @Test
    fun `an unclaimed duel has no friend and an author is not one`() = runTest {
        duel("AAAAA2", ME, opponent = null, author = "author-uid")

        assertEquals(emptySet<String>(), friends().first())
    }

    @Test
    fun `signed out is empty`() = runTest {
        duel("AAAAA2", ME, BO)
        identity.currentIdentity = null

        assertEquals(emptySet<String>(), friends().first())
    }

    @Test
    fun `a match arriving adds its opponent`() = runTest {
        val seen = mutableListOf<Set<String>>()
        friends().onEach { seen += it }.launchIn(kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(emptySet<String>(), seen.last())

        duel("AAAAA2", ME, BO)
        assertEquals(setOf(BO.uid), seen.last())

        duel("AAAAA3", CAT, ME)
        assertEquals(setOf(BO.uid, CAT.uid), seen.last())
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 11, 13)
        val ME = Player("me-uid", "Me", null)
        val BO = Player("bo-uid", "Bo", null)
        val CAT = Player("cat-uid", "Cat", null)
    }
}
