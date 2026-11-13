package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView.Target
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A day asks LinkedIn for its data only while a screen shows it, once, and only after it has stayed there. */
class ObserveLinkedInDayViewTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val game = TestGames.alpha
    private val dayA = LocalDate.of(2026, 11, 5)
    private val dayB = LocalDate.of(2026, 11, 6)
    private val profile = "member-1"
    private val paused = LinkState.Paused(profile, Kind.Challenge)

    private val days = FakeLinkedInDays()
    private val observe = ObserveLinkedInDay(days, TestGames.registry(listOf(game)))

    /** Stands in for the screen: the (game, day) it shows, null while it shows none. */
    private val target = MutableStateFlow<Target?>(null)

    private fun holder(link: LinkedInLink = FakeLinkedInLink()) = ObserveLinkedInDayView(observe, SyncLinkedInDay(days, 500), link, days)

    /** A linked day whose own result is not known yet, so [inFlight] and [lastSync] decide what it shows. */
    private fun data(
        inFlight: Boolean = false,
        lastSync: LinkedInSync? = null,
        own: LinkedInOwnState = LinkedInOwnState.Unknown,
    ) = LinkedInDayData(
        link = LinkState.Connected(profile),
        delta = 3,
        boardInWindow = true,
        inFlight = inFlight,
        lastSync = lastSync,
        own = own,
        ghosts = emptyList(),
        boardFetchedAtMs = null,
    )

    /** A screen that stays on [holder]'s view until the test ends, with everything it was shown. */
    private fun TestScope.shownBy(holder: ObserveLinkedInDayView): List<LinkedInDayView?> {
        val views = mutableListOf<LinkedInDayView?>()
        holder(target).onEach { views += it }.launchIn(backgroundScope)
        return views
    }

    /** Moves the clock to exactly [ms] later, work due at that instant included. */
    private fun TestScope.after(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun noCollectorMeansNoCall() = runTest(main.testDispatcher) {
        holder()(target)
        target.value = Target(game, dayA)

        after(HOUR_MS)

        assertEquals(emptyList<Any>(), days.syncCalls)
    }

    @Test
    fun collectingASyncsTheDayOnceAfterTheDebounce() = runTest(main.testDispatcher) {
        target.value = Target(game, dayA)
        shownBy(holder())

        after(499)
        assertEquals(emptyList<Any>(), days.syncCalls)

        after(1)
        assertEquals(listOf(game to dayA), days.syncCalls)

        after(HOUR_MS)
        assertEquals(listOf(game to dayA), days.syncCalls)
    }

    @Test
    fun scrubbingFiveDaysWithinTheDebounceMakesNoCall() = runTest(main.testDispatcher) {
        shownBy(holder())

        repeat(5) { step ->
            target.value = Target(game, dayA.plusDays(step.toLong()))
            after(100)
        }
        // The fifth day came on screen at 400 ms, so its wait ends at 900 ms.
        after(399)
        assertEquals(emptyList<Any>(), days.syncCalls)

        after(1)
        assertEquals(listOf(game to dayA.plusDays(4)), days.syncCalls)
    }

    @Test
    fun leavingBeforeTheDebounceCancelsTheSync() = runTest(main.testDispatcher) {
        target.value = Target(game, dayA)
        val screen = holder()(target).launchIn(this)

        after(300)
        screen.cancel()
        after(HOUR_MS)

        assertEquals(emptyList<Any>(), days.syncCalls)
    }

    @Test
    fun aNullTargetShowsNothingAndCallsNothing() = runTest(main.testDispatcher) {
        days.emit(game, dayA, data(inFlight = true))
        val views = shownBy(holder())

        after(HOUR_MS)
        assertEquals(listOf<LinkedInDayView?>(null), views)
        assertEquals(emptyList<Any>(), days.syncCalls)

        target.value = Target(game, dayA)
        runCurrent()
        assertEquals(observe(game, dayA).first(), views.last())
    }

    @Test
    fun theViewFollowsTheTarget() = runTest(main.testDispatcher) {
        days.emit(game, dayA, data(inFlight = true))
        days.emit(game, dayB, data(own = LinkedInOwnState.NotPlayed))
        target.value = Target(game, dayA)
        val views = shownBy(holder())

        target.value = Target(game, dayB)
        runCurrent()

        // The old day's view is dropped the moment the day changes, so a slow store never leaves it on the card.
        assertEquals(listOf(observe(game, dayA).first(), null, observe(game, dayB).first()), views)
    }

    @Test
    fun aDayThatWillBeReadShowsSyncingFromItsFirstViewAndNeverTheLastDaysState() = runTest(main.testDispatcher) {
        days.emit(game, dayA, data(own = LinkedInOwnState.NotPlayed))
        days.emit(game, dayB, data(lastSync = LinkedInSync.Done))
        target.value = Target(game, dayA)
        val views = shownBy(holder())

        target.value = Target(game, dayB)
        runCurrent()
        // Nothing of day B is settled until its sync has answered: not before the debounce, and not by a stale check.
        assertEquals(listOf(LinkedInDayStatus.DidNotPlay, null, LinkedInDayStatus.Loading), views.map { it?.status })

        after(500)
        assertEquals(LinkedInDayStatus.Idle, views.last()?.status)
        assertEquals(listOf(game to dayB), days.syncCalls)
    }

    @Test
    fun aDaySettledBeforeItsSyncShowsThatStateAtOnce() = runTest(main.testDispatcher) {
        days.emit(game, dayA, data(lastSync = LinkedInSync.Done))
        days.emit(game, dayB, data(own = LinkedInOwnState.NotPlayed))
        target.value = Target(game, dayA)
        val views = shownBy(holder())

        target.value = Target(game, dayB)
        after(HOUR_MS)

        assertEquals(listOf(LinkedInDayStatus.Loading, null, LinkedInDayStatus.DidNotPlay), views.map { it?.status })
    }

    @Test
    fun aDayThatIsNeverReadShowsItsSettledStateFromTheFirstViewAtOnce() = runTest(main.testDispatcher) {
        days.setWillRead(game, dayA, false)
        days.setWillRead(game, dayB, false)
        days.emit(game, dayA, data(lastSync = LinkedInSync.Done))
        days.emit(game, dayB, data(lastSync = LinkedInSync.Done))
        target.value = Target(game, dayA)
        val views = shownBy(holder())

        target.value = Target(game, dayB)
        runCurrent()
        after(HOUR_MS)

        assertEquals(listOf(LinkedInDayStatus.Idle, null, LinkedInDayStatus.Idle), views.map { it?.status })
    }

    @Test
    fun aFutureDayShowsItsSettledStateFromTheFirstViewAtOnce() = runTest(main.testDispatcher) {
        days.setWillRead(game, dayB, false)
        days.emit(game, dayA, data(lastSync = LinkedInSync.Done))
        days.emit(game, dayB, data(lastSync = LinkedInSync.Done))
        target.value = Target(game, dayA)
        val views = shownBy(holder())

        target.value = Target(game, dayB)
        runCurrent()
        after(HOUR_MS)

        assertEquals(listOf(LinkedInDayStatus.Loading, null, LinkedInDayStatus.Idle), views.map { it?.status })
    }

    @Test
    fun aSyncThatIsCancelledClearsSyncingInsteadOfLeavingItUp() = runTest(main.testDispatcher) {
        days.emit(game, dayA, data(lastSync = LinkedInSync.Done))
        val gate = CompletableDeferred<Unit>()
        days.syncGate = gate
        target.value = Target(game, dayA)
        val views = shownBy(holder())
        after(500)
        assertEquals(LinkedInDayStatus.Loading, views.last()?.status)

        gate.cancel()
        runCurrent()

        assertEquals(LinkedInDayStatus.Idle, views.last()?.status)
    }

    @Test
    fun aSyncThatThrowsEndsTheViewWithItsFailureAndNeverLeavesSyncingUp() = runTest(main.testDispatcher) {
        days.emit(game, dayA, data(lastSync = LinkedInSync.Done))
        val gate = CompletableDeferred<Unit>()
        days.syncGate = gate
        target.value = Target(game, dayA)
        val failures = mutableListOf<Throwable>()
        val views = mutableListOf<LinkedInDayView?>()
        holder()(target).onEach { views += it }.catch { failures += it }.launchIn(backgroundScope)
        after(500)
        assertEquals(LinkedInDayStatus.Loading, views.last()?.status)

        gate.completeExceptionally(IllegalStateException("boom"))
        runCurrent()

        assertEquals(listOf("boom"), failures.map { it.message })
    }

    @Test
    fun retryShowsSyncingAtOnceWithoutBlankingTheChip() = runTest(main.testDispatcher) {
        val holder = holder()
        days.emit(game, dayA, data(lastSync = LinkedInSync.Offline))
        target.value = Target(game, dayA)
        val views = shownBy(holder)
        after(500)
        assertEquals(LinkedInDayStatus.Offline, views.last()?.status)

        holder.retry()
        runCurrent()

        assertEquals(LinkedInDayStatus.Loading, views.last()?.status)
        assertEquals(false, views.drop(1).any { it == null })
    }

    @Test
    fun retrySyncsTheShownDayAgain() = runTest(main.testDispatcher) {
        val holder = holder()
        target.value = Target(game, dayA)
        shownBy(holder)
        after(500)
        assertEquals(listOf(game to dayA), days.syncCalls)

        holder.retry()

        after(499)
        assertEquals(listOf(game to dayA), days.syncCalls)
        after(1)
        assertEquals(listOf(game to dayA, game to dayA), days.syncCalls)
    }

    @Test
    fun retryAndResumeWithoutACollectorDoNothing() = runTest(main.testDispatcher) {
        val link = FakeLinkedInLink(paused)
        val holder = holder(link)
        target.value = Target(game, dayA)
        holder(target)

        holder.retry()
        holder.resume()
        after(HOUR_MS)

        assertEquals(emptyList<Any>(), days.syncCalls)
        assertEquals(paused, link.state.first())

        // The Resume nobody was there for is gone: a screen arriving later does not lift the pause with it.
        shownBy(holder)
        runCurrent()
        assertEquals(paused, link.state.first())
    }

    @Test
    fun resumeWithNoDayShownLeavesTheLinkPaused() = runTest(main.testDispatcher) {
        val link = FakeLinkedInLink(paused)
        val holder = holder(link)
        shownBy(holder)

        holder.resume()
        after(HOUR_MS)

        assertEquals(paused, link.state.first())
        assertEquals(emptyList<Any>(), days.syncCalls)
    }

    @Test
    fun resumeClearsTheLatchThenSyncs() = runTest(main.testDispatcher) {
        val link = FakeLinkedInLink(paused)
        val holder = holder(link)
        target.value = Target(game, dayA)
        shownBy(holder)
        after(500)
        assertEquals(listOf(game to dayA), days.syncCalls)

        holder.resume()
        runCurrent()
        assertEquals(LinkState.Connected(profile), link.state.first())

        after(499)
        assertEquals(listOf(game to dayA), days.syncCalls)
        after(1)
        assertEquals(listOf(game to dayA, game to dayA), days.syncCalls)
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
