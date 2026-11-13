package com.ivandsky.puzzlebox.profile.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.domain.linkedin.CreateLinkedInLink
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.profile.linkedin.LinkedInLoginViewModel.Step
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The login route's state holder: the warning gates everything, one completed login sends one
 * `check`, and every failure leaves the link not connected.
 */
class LinkedInLoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val zip = GameId("zip")
    private val session = LinkedInAccountSession(cookieHeader = "cookies", csrfToken = "token", userAgent = "agent")
    private val source = FakeLinkedInAccountSource()
    private val link = FakeLinkedInLink()
    private val daily = FakeDailyRepository().apply { setLinkedInGameType(zip, 6) }

    private fun viewModel(cookies: LinkedInSessionCookies, checks: LinkedInAccountSource = source) =
        LinkedInLoginViewModel(
            createLink = CreateLinkedInLink(checks, link, daily, TestGames.registry(listOf(zip))) { 1_700_000_000_000 },
            cookies = cookies,
        )

    /** Every step the holder passes through, none conflated away. */
    private fun TestScope.stepsOf(vm: LinkedInLoginViewModel): List<Step> {
        val steps = mutableListOf<Step>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.step.collect { steps += it } }
        return steps
    }

    @Test
    fun `the warning ignores everything but accept`() = runTest(dispatcher) {
        val cookies = SettableCookies(session)
        source.checkReply = LinkedInRead.Ok("me")
        val vm = viewModel(cookies)
        val steps = stepsOf(vm)

        vm.onPageFinished()
        vm.onRetry()
        advanceUntilIdle()

        assertEquals(listOf<Step>(Step.Warning), steps)
        assertEquals(0, cookies.reads)
        assertTrue(source.calls.isEmpty())
        assertEquals(LinkState.NotConnected, link.state.first())
    }

    @Test
    fun `accepting with a stored session connects without the web view`() = runTest(dispatcher) {
        val cookies = SettableCookies(session)
        source.checkReply = LinkedInRead.Ok("me")
        val vm = viewModel(cookies)
        val steps = stepsOf(vm)

        vm.onAccept()
        advanceUntilIdle()

        assertEquals(listOf(Step.Warning, Step.Checking, Step.Done), steps)
        assertEquals(listOf("check:6"), source.calls)
        assertEquals(LinkState.Connected("me"), link.state.first())
    }

    @Test
    fun `accepting without a session waits in the web view until the login completes`() = runTest(dispatcher) {
        val cookies = SettableCookies(null)
        source.checkReply = LinkedInRead.Ok("me")
        val vm = viewModel(cookies)
        val steps = stepsOf(vm)

        vm.onAccept()
        advanceUntilIdle()
        assertEquals(listOf(Step.Warning, Step.SignIn), steps)

        // A page of the login flow (the form, a 2FA or captcha page) finishes with no session yet.
        vm.onPageFinished()
        advanceUntilIdle()
        assertEquals(listOf(Step.Warning, Step.SignIn), steps)
        assertTrue(source.calls.isEmpty())

        cookies.session = session
        vm.onPageFinished()
        vm.onPageFinished()
        advanceUntilIdle()

        assertEquals(listOf(Step.Warning, Step.SignIn, Step.Checking, Step.Done), steps)
        assertEquals(listOf("check:6"), source.calls)
        assertEquals(LinkState.Connected("me"), link.state.first())
    }

    @Test
    fun `one completed login sends exactly one check`() = runTest(dispatcher) {
        val cookies = SettableCookies(session)
        val held = HeldLinkedInAccountSource(source)
        source.checkReply = LinkedInRead.Ok("me")
        val vm = viewModel(cookies, held)
        val events = listOf(vm::onAccept, vm::onPageFinished, vm::onPageFinished, vm::onRetry)

        vm.onAccept()
        // Before the check: the stored session has not even been read yet.
        events.forEach { it() }
        runCurrent()
        // During the check: the one call is out and has not answered.
        assertEquals(Step.Checking, vm.step.value)
        events.forEach { it() }
        runCurrent()
        assertEquals(Step.Checking, vm.step.value)
        held.answer.complete(Unit)
        advanceUntilIdle()
        // After the check.
        events.forEach { it() }
        advanceUntilIdle()

        assertEquals(Step.Done, vm.step.value)
        assertEquals(listOf("check:6"), source.calls)
        assertEquals(1, cookies.reads)
    }

    @Test
    fun `offline fails without connecting and a retry checks once more`() = runTest(dispatcher) {
        val cookies = SettableCookies(session)
        source.checkReply = LinkedInRead.Offline
        val vm = viewModel(cookies)

        vm.onAccept()
        advanceUntilIdle()

        assertEquals(Step.Failed("No connection. Check your network and try again."), vm.step.value)
        assertEquals(LinkState.NotConnected, link.state.first())
        assertFalse(cookies.cleared)

        source.checkReply = LinkedInRead.Ok("me")
        vm.onRetry()
        vm.onRetry()
        advanceUntilIdle()

        assertEquals(Step.Done, vm.step.value)
        assertEquals(listOf("check:6", "check:6"), source.calls)
        assertEquals(LinkState.Connected("me"), link.state.first())
    }

    @Test
    fun `a challenge fails without connecting or clearing the login`() = runTest(dispatcher) {
        val cookies = SettableCookies(session)
        source.checkReply = LinkedInRead.Rejected(LinkedInRead.Rejected.Kind.Challenge)
        val vm = viewModel(cookies)

        vm.onAccept()
        advanceUntilIdle()

        assertEquals(Step.Failed("LinkedIn didn't accept the check. Try again in a while."), vm.step.value)
        assertEquals(LinkState.NotConnected, link.state.first())
        assertFalse(cookies.cleared)
        assertEquals(listOf("check:6"), source.calls)
    }

    @Test
    fun `a stale session is cleared and the fresh login gets its own single check`() = runTest(dispatcher) {
        val cookies = SettableCookies(session)
        source.checkReply = LinkedInRead.Rejected(LinkedInRead.Rejected.Kind.SessionExpired)
        val vm = viewModel(cookies)
        val steps = stepsOf(vm)

        vm.onAccept()
        advanceUntilIdle()
        // The login page finishing with the cookies wiped is not a login.
        vm.onPageFinished()
        vm.onRetry()
        advanceUntilIdle()

        assertTrue(cookies.cleared)
        assertEquals(listOf(Step.Warning, Step.Checking, Step.SignIn), steps)
        assertEquals(LinkState.NotConnected, link.state.first())
        assertEquals(listOf("check:6"), source.calls)

        source.checkReply = LinkedInRead.Ok("me")
        cookies.session = session
        vm.onPageFinished()
        vm.onPageFinished()
        advanceUntilIdle()

        assertEquals(listOf(Step.Warning, Step.Checking, Step.SignIn, Step.Checking, Step.Done), steps)
        assertEquals(listOf("check:6", "check:6"), source.calls)
        assertEquals(LinkState.Connected("me"), link.state.first())
    }

    @Test
    fun `cookies that vanished before the check send the player back to sign in`() = runTest(dispatcher) {
        val cookies = SettableCookies(session)
        source.checkReply = LinkedInRead.SignedOut
        val vm = viewModel(cookies)

        vm.onAccept()
        advanceUntilIdle()

        assertEquals(Step.SignIn, vm.step.value)
        assertEquals(LinkState.NotConnected, link.state.first())
        assertFalse(cookies.cleared)
    }
}

/**
 * A session store whose login can finish mid-test; counts reads so a test can prove none happened.
 * A read suspends as the real store's hop to the main thread does, so two events can both be mid-read.
 */
private class SettableCookies(var session: LinkedInAccountSession?) : LinkedInSessionCookies {
    var reads = 0
        private set
    var cleared = false
        private set

    override suspend fun current(): LinkedInAccountSession? {
        reads++
        yield()
        return session
    }

    override suspend fun clear() {
        cleared = true
        session = null
    }
}

/** Records the `check` on [inner], then holds its reply until [answer] completes, so events can land mid-call. */
private class HeldLinkedInAccountSource(private val inner: LinkedInAccountSource) : LinkedInAccountSource by inner {
    val answer = CompletableDeferred<Unit>()

    override suspend fun check(gameTypeId: Int): LinkedInRead<String> {
        val reply = inner.check(gameTypeId)
        answer.await()
        return reply
    }
}
