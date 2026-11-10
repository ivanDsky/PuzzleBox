package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The connect step: one `check`, then the link. Anything but a profile id writes nothing. */
class CreateLinkedInLinkTest {

    private val zip = GameId("zip")
    private val queens = GameId("queens")
    private val strands = GameId("strands")
    private val now = 1_700_000_000_000

    private val source = FakeLinkedInAccountSource()
    private val daily = FakeDailyRepository().apply {
        setLinkedInGameType(zip, 6)
        setLinkedInGameType(queens, 3)
    }

    private fun create(link: FakeLinkedInLink, games: List<GameId> = listOf(zip, queens, strands)) =
        CreateLinkedInLink(source, link, daily, TestGames.registry(games)) { now }

    @Test
    fun `a good check connects the profile with the consent time`() = runTest {
        val link = FakeLinkedInLink()
        source.checkReply = LinkedInRead.Ok("me")

        val result = create(link)()

        assertEquals(LinkedInRead.Ok(Unit), result)
        assertEquals(listOf("me" to now), link.connects)
        assertEquals(LinkState.Connected("me"), link.state.first())
        assertEquals(listOf("check:6"), source.calls)
    }

    @Test
    fun `the first LinkedIn game in registry order supplies the type`() = runTest {
        val link = FakeLinkedInLink()
        source.checkReply = LinkedInRead.Ok("me")

        create(link, listOf(strands, queens, zip))()

        assertEquals(listOf("check:3"), source.calls)
    }

    @Test
    fun `a failed check writes nothing`() = runTest {
        val replies = listOf(LinkedInRead.Offline, LinkedInRead.SignedOut) +
            LinkedInRead.Rejected.Kind.entries.map { LinkedInRead.Rejected(it) }
        for (reply in replies) {
            val link = FakeLinkedInLink()
            source.checkReply = reply

            val result = create(link)()

            assertEquals(reply, result)
            assertTrue(link.connects.isEmpty())
            assertEquals(LinkState.NotConnected, link.state.first())
        }
    }

    @Test
    fun `connecting again from a paused link reconnects`() = runTest {
        val link = FakeLinkedInLink(LinkState.Paused("me", LinkedInRead.Rejected.Kind.SessionExpired))
        source.checkReply = LinkedInRead.Ok("me")

        create(link)()

        assertEquals(LinkState.Connected("me"), link.state.first())
    }
}
