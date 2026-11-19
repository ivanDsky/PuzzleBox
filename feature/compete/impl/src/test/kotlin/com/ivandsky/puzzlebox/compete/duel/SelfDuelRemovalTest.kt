package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.compete.domain.DeleteSelfDuel
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The remove-confirm of a self duel when the delete throws: back to Idle, the failure reported, no crash. */
class SelfDuelRemovalTest {

    private val code = ShareCode.parse("K7Q2MX")!!

    @Test
    fun aThrowingDeleteEndsIdleReportsTheCauseAndNeverCallsOnRemoved() = runTest {
        val room = IllegalStateException("disk full")
        val broken = object : SelfMatchRepository by FakeSelfMatchRepository() {
            override fun observe(code: ShareCode): Flow<MatchFetch> = flow { throw room }
        }
        val reported = mutableListOf<Throwable>()
        var removed = false
        val removal = SelfDuelRemoval(
            code = code,
            deleteSelfDuel = DeleteSelfDuel(broken, FakeMatchResultOutbox(), FakeSavedPlayRepository(), FakeMatchLocalRepository()),
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            onRemoved = { removed = true },
            failures = FailureLog { _, _, cause -> reported += cause },
        )

        removal.ask()
        removal.remove()

        assertEquals(RemovalStep.Idle, removal.step.value)
        assertFalse(removed)
        assertEquals(listOf<Throwable>(room), reported)
    }
}
