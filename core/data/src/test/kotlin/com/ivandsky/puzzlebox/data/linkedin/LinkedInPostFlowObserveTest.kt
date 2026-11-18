package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the button says: the rule's answer, a running post over it, and the outcomes only this process knows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInPostFlowObserveTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rigs = mutableListOf<LinkedInPostRig>()

    @After
    fun tearDown() {
        rigs.forEach { it.close() }
    }

    private fun TestScope.rig(): LinkedInPostRig = LinkedInPostRig(this, folder.newFolder()).also { rigs += it }

    private suspend fun LinkedInPostRig.shown(door: LinkedInPosts = posts): LinkedInPostState = door.observe(game, today).first().state

    /** Every state the button of today went through, from the moment of the call. */
    private fun TestScope.buttonOf(rig: LinkedInPostRig): List<LinkedInPostState> {
        val seen = mutableListOf<LinkedInPostState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { rig.posts.observe(rig.game, rig.today).map { it.state }.toList(seen) }
        return seen
    }

    /** The button of a day whose send got [reply], watched from before the tap to after the outcome. */
    private suspend fun TestScope.buttonThrough(reply: LinkedInSendResult): List<LinkedInPostState> {
        val rig = rig()
        rig.postable()
        val onTheWire = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()
        rig.poster.sendReply = reply
        rig.poster.onSend = {
            onTheWire.complete(Unit)
            answer.await()
        }
        val seen = buttonOf(rig)

        val tap = async { rig.submit() }
        onTheWire.await()
        answer.complete(Unit)
        tap.await()
        testScheduler.runCurrent()
        return seen.toList()
    }

    @Test
    fun `the button goes from Ready through Sending straight to the outcome of the send`() = runTest {
        val ends = mapOf(
            LinkedInSendResult.Posted to LinkedInPostState.Posted,
            LinkedInSendResult.AlreadyPlayed to LinkedInPostState.AlreadyOnLinkedIn,
            LinkedInSendResult.Unknown to LinkedInPostState.Unknown,
            LinkedInSendResult.Offline to LinkedInPostState.Offline,
            LinkedInSendResult.Rejected(Kind.Challenge) to LinkedInPostState.Paused,
        )

        val seen = ends.keys.associateWith { buttonThrough(it) }

        assertEquals(ends.mapValues { (_, end) -> listOf(LinkedInPostState.Ready, LinkedInPostState.Sending, end) }, seen)
    }

    @Test
    fun `the button is Ready exactly for a day whose tap would ask LinkedIn`() = runTest {
        val import = LinkedInPostRig.SOLVE.copy(source = CompletionSource.LinkedIn)
        val cases = mapOf<String, suspend LinkedInPostRig.() -> Unit>(
            "today's own solve" to { postable() },
            "posting off" to { postable(); link.setPosting(null) },
            "never linked" to { postable(); link.disconnect() },
            "link paused" to { postable(); link.pause(Kind.RateLimited) },
            "not solved" to { postable(); completions.setRecords(emptyList()) },
            "imported first, replayed in the app after" to
                { completions.onCompleted(import, null); postable() },
            "a duel leg" to { postable(); completions.setRecords(listOf(LinkedInPostRig.SOLVE.copy(matchCode = "DUEL42"))) },
            "solve stored without its replay" to { completions.onCompleted(LinkedInPostRig.SOLVE, null); postable() },
            "a game with no encoder" to { registry = GameRegistry(listOf(TestGames.plugin(ReplayFixtures.GAME))); postable() },
            "an encoder that declines the solve" to
                { registry = GameRegistry(listOf(TestGames.encoderPlugin { null })); postable() },
            "no LinkedIn route" to { postable(); daily.setLinkedInGamePath(game, null) },
            "no LinkedIn type id" to { postable(); daily.setLinkedInGameType(game, null) },
            "yesterday's solve" to { postable(); calendar.setAll(today.plusDays(1)) },
        )

        val seen = cases.mapValues { (_, arrange) ->
            val rig = rig()
            rig.arrange()
            val button = rig.shown()
            rig.submit()
            button to (rig.source.calls + rig.poster.calls).isNotEmpty()
        }

        val hiddenAndSilent = LinkedInPostState.Hidden to false
        assertEquals(
            mapOf(
                "today's own solve" to (LinkedInPostState.Ready to true),
                "posting off" to hiddenAndSilent,
                "never linked" to hiddenAndSilent,
                "link paused" to (LinkedInPostState.Paused to false),
                "not solved" to hiddenAndSilent,
                "imported first, replayed in the app after" to hiddenAndSilent,
                "a duel leg" to hiddenAndSilent,
                "solve stored without its replay" to hiddenAndSilent,
                "a game with no encoder" to hiddenAndSilent,
                "an encoder that declines the solve" to hiddenAndSilent,
                "no LinkedIn route" to hiddenAndSilent,
                "no LinkedIn type id" to hiddenAndSilent,
                "yesterday's solve" to hiddenAndSilent,
            ),
            seen,
        )
    }

    @Test
    fun `a send with no answer still reads as unsure after a restart, and a post as posted on any later day`() = runTest {
        val unsure = rig().apply { postable(); poster.sendReply = LinkedInSendResult.Unknown; submit() }
        val posted = rig().apply { postable(); submit(); calendar.setAll(today.plusDays(2)) }

        assertEquals(LinkedInPostState.Unknown, unsure.shown(unsure.door()))
        assertEquals(LinkedInPostState.Posted, posted.shown(posted.door()))
    }

    @Test
    fun `a result LinkedIn already held is told for the rest of the process, and the day can be tapped again after it`() = runTest {
        val rig = rig()
        rig.postable()
        rig.source.respondOwn("me", 6, 712, LinkedInRead.Ok(LinkedInOwnResult.Solved(timeSeconds = 70, hintFree = true)))
        rig.submit()

        assertEquals(LinkedInPostState.AlreadyOnLinkedIn, rig.shown())
        assertEquals(LinkedInPostState.Ready, rig.shown(rig.door()))
    }

    @Test
    fun `an offline tap shows Offline until the next tap gets through`() = runTest {
        val rig = rig()
        rig.postable()
        rig.source.respondOwn("me", 6, 712, LinkedInRead.Offline)
        rig.submit()
        val afterOffline = rig.shown()
        rig.source.respondOwn("me", 6, 712, LinkedInRead.Ok(LinkedInOwnResult.NotPlayed))

        rig.submit()

        assertEquals(LinkedInPostState.Offline, afterOffline)
        assertEquals(LinkedInPostState.Posted, rig.shown())
    }

    @Test
    fun `a link paused elsewhere replaces an offline note`() = runTest {
        val rig = rig()
        rig.postable()
        rig.source.respondOwn("me", 6, 712, LinkedInRead.Offline)
        rig.submit()

        rig.link.pause(Kind.Challenge)

        assertEquals(LinkedInPostState.Paused, rig.shown())
    }

    @Test
    fun `a page left open past the game's midnight keeps its button until a tap is refused`() = runTest {
        val rig = rig()
        rig.postable()
        val seen = buttonOf(rig)
        rig.calendar.setAll(rig.today.plusDays(1))

        val result = rig.submit()
        testScheduler.runCurrent()

        assertEquals(LinkedInPostState.NotToday, result)
        assertEquals(listOf(LinkedInPostState.Ready, LinkedInPostState.NotToday), seen.toList())
        assertEquals(emptyList<String>(), rig.source.calls + rig.poster.calls)
    }

    @Test
    fun `a tutorial page is shown after the tap and leaves the link unpaused`() = runTest {
        val rig = rig()
        rig.postable()
        rig.poster.pageReply = LinkedInRead.Ok(LinkedInPostRig.PAGE.copy(isTutorial = true, puzzleId = 2))
        val seen = buttonOf(rig)

        val result = rig.submit()
        testScheduler.runCurrent()

        assertEquals(LinkedInPostState.Tutorial, result)
        assertEquals(listOf(LinkedInPostState.Ready, LinkedInPostState.Sending, LinkedInPostState.Tutorial), seen.toList())
        assertEquals(LinkState.Connected("me"), rig.link.state.first())
    }

    @Test
    fun `a post that throws after the marker was stored leaves the day unsure, not sending`() = runTest {
        val rig = rig()
        rig.postable()
        rig.poster.onSend = { throw IllegalStateException("socket closed") }

        runCatching { rig.submit() }

        assertEquals(LinkedInPostState.Unknown, rig.shown())
    }

    @Test
    fun `posting turned off hides even a posted day`() = runTest {
        val rig = rig()
        rig.postable()
        rig.submit()

        rig.link.setPosting(null)

        assertEquals(LinkedInPostState.Hidden, rig.shown())
    }

    @Test
    fun `a day whose own LinkedIn result the phone already read says Already on LinkedIn at once and a tap sends nothing`() = runTest {
        val rig = rig()
        rig.postable()
        rig.completions.onCompleted(LinkedInPostRig.SOLVE.copy(time = 70_000L, isRecord = false, source = CompletionSource.LinkedIn), null)

        val first = rig.shown()
        val answer = rig.submit()

        assertEquals(LinkedInPostState.AlreadyOnLinkedIn, first)
        assertEquals(LinkedInPostState.AlreadyOnLinkedIn, answer)
        assertEquals(emptyList<String>(), rig.source.calls + rig.poster.calls)
    }

    @Test
    fun `a LinkedIn result of another day does not say Already on LinkedIn`() = runTest {
        val rig = rig()
        rig.postable()
        val otherDay = TestGames.Ref(rig.game, PuzzleKey.Daily(rig.today.minusDays(1), 711))
        rig.completions.onCompleted(LinkedInPostRig.SOLVE.copy(puzzleRef = otherDay, isRecord = false, source = CompletionSource.LinkedIn), null)

        assertEquals(LinkedInPostState.Ready, rig.shown())
    }
}
