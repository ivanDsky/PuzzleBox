package com.ivandsky.puzzlebox.compete.tab

import com.ivandsky.puzzlebox.compete.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.compete.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The photo the Duels tab's self rows put on Past you: the player's own, when signed in with one. */
class DuelsTabPastYouPhotoTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now = Instant.parse("2026-11-09T10:00:00Z")
    private val server = FakeMatchRepository().apply { this.now = this@DuelsTabPastYouPhotoTest.now }
    private val local = FakeMatchLocalRepository()

    private fun TestScope.photoOf(who: Identity?): String? {
        val identity = FakeIdentityService(who)
        server.signedInUid = who?.uid
        val vm = DuelsTabViewModel(
            observeMyDuels = ObserveMyDuels(server, FakeSelfMatchRepository(), FakeMatchResultOutbox(), local, identity) { now },
            updateSeat = UpdateMatchSeat(server, local, identity),
            identity = identity,
            clock = { now },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm.state.value.selfPhotoUrl
    }

    @Test
    fun `the signed-in player's own photo goes to Past you`() = runTest {
        assertEquals(PHOTO, photoOf(Identity("ann", "Ann", PHOTO)))
    }

    @Test
    fun `no photo or signed out keeps the initial`() = runTest {
        assertNull(photoOf(Identity("ann", "Ann", null)))
        assertNull(photoOf(null))
    }

    private companion object {
        const val PHOTO = "https://example.test/ann.jpg"
    }
}
