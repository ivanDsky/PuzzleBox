package com.ivandsky.puzzlebox.compete.tab

import com.ivandsky.puzzlebox.compete.create.DuelChallenge
import com.ivandsky.puzzlebox.compete.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.compete.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.WriteResult
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Duels tab's state holder over a real [ObserveMyDuels] and [UpdateMatchSeat] on the in-memory server: Ann
 * challenges, Bob is the signed-in viewer whose Invited rows the buttons act on.
 */
class DuelsTabViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val code = ShareCode.parse("K7Q2MX")!!
    private val other = ShareCode.parse("B4RT9P")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)
    private val cat = Player("cat", "Cat", null)
    private val now = Instant.parse("2026-11-09T10:00:00Z")
    private val date = LocalDate.of(2026, 11, 9)

    private val server = FakeMatchRepository().apply { this.now = this@DuelsTabViewModelTest.now }
    private val local = FakeMatchLocalRepository()
    private val identity = FakeIdentityService()

    /** Scripted seat writes on top of [server]: [claimGate] holds a claim open, [claimOutcome] and [declineOutcome] override it. */
    private var claimGate: CompletableDeferred<Unit>? = null
    private var claimOutcome: ClaimResult? = null
    private var declineOutcome: WriteResult? = null
    private val claimCalls = mutableListOf<ShareCode>()
    private val declineCalls = mutableListOf<ShareCode>()
    private val repo = object : MatchRepository by server {
        override suspend fun claim(code: ShareCode, me: Player): ClaimResult {
            claimCalls += code
            claimGate?.await()
            return claimOutcome ?: server.claim(code, me)
        }

        override suspend fun decline(code: ShareCode): WriteResult {
            declineCalls += code
            return declineOutcome ?: server.decline(code)
        }
    }

    private fun viewAs(player: Player?) {
        identity.currentIdentity = player?.let { Identity(it.uid, it.name, null) }
        server.signedInUid = player?.uid
    }

    private fun viewModel() = DuelsTabViewModel(
        observeMyDuels = ObserveMyDuels(repo, FakeSelfMatchRepository(), FakeMatchResultOutbox(), local, identity) { now },
        updateSeat = UpdateMatchSeat(repo, local, identity),
        identity = identity,
        clock = { now },
        releaseFlags = FakeReleaseFlags(),
    )

    private fun TestScope.started(vm: DuelsTabViewModel): DuelsTabViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm
    }

    private fun TestScope.opened(vm: DuelsTabViewModel): MutableList<ShareCode> {
        val seen = mutableListOf<ShareCode>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.openDuel.toList(seen) }
        return seen
    }

    /** Ann opens [code]; with [invited] it is a rematch invite to Bob, without it Bob holds it as a link invite. */
    private suspend fun annChallenges(invited: Player? = null, newCode: ShareCode = code) {
        viewAs(ann)
        val legs = listOf(
            MatchLeg(LegRef.Daily(TestGames.alpha, date, 1), null),
            MatchLeg(LegRef.Daily(TestGames.beta, date, 2), null),
        )
        server.create(MatchDraft(newCode, 24, ann, invited?.uid, legs, includeFriends = false, rematchOf = null))
        viewAs(bob)
        if (invited == null) local.remember(newCode, LocalRole.Invite, "Ann")
    }

    private fun DuelsTabViewModel.rowCodes() = state.value.rows.map { it.code }

    @Test
    fun acceptOnARowClaimsThenOpensTheDuel() = runTest {
        annChallenges()
        val vm = started(viewModel())
        val opened = opened(vm)
        assertEquals(listOf(code), vm.rowCodes())

        vm.accept(code)

        assertEquals(bob.uid, server.matches.getValue(code).opponent?.uid)
        assertEquals(listOf(code), opened)
        assertFalse(code in vm.state.value.inFlight)
    }

    @Test
    fun declineOnARowDeclinesRemotelyForARematchInviteAndRemovesTheRow() = runTest {
        annChallenges(invited = bob)
        val vm = started(viewModel())
        assertEquals(listOf(code), vm.rowCodes())

        vm.decline(code)

        assertEquals(listOf(code), declineCalls)
        assertTrue(server.matches.getValue(code).declined)
        assertEquals(emptyList<ShareCode>(), vm.rowCodes())
    }

    @Test
    fun declineOfALinkInviteOnlyDismissesLocally() = runTest {
        annChallenges()
        val vm = started(viewModel())
        assertEquals(listOf(code), vm.rowCodes())

        vm.decline(code)

        assertEquals(emptyList<ShareCode>(), declineCalls)
        assertFalse(server.matches.getValue(code).declined)
        assertEquals(emptyList<ShareCode>(), vm.rowCodes())
    }

    @Test
    fun acceptThatLosesTheRaceShowsAlreadyTakenOnTheRow() = runTest {
        annChallenges()
        val vm = started(viewModel())
        val opened = opened(vm)
        claimOutcome = ClaimResult.Taken

        vm.accept(code)

        assertEquals(InviteNote.AlreadyTaken, vm.state.value.notes[code])
        assertEquals(emptyList<ShareCode>(), opened)
    }

    @Test
    fun acceptOnACancelledDuelShowsCancelled() = runTest {
        annChallenges()
        val vm = started(viewModel())
        claimOutcome = ClaimResult.Cancelled

        vm.accept(code)

        assertEquals(InviteNote.Cancelled, vm.state.value.notes[code])
    }

    @Test
    fun acceptOfflineKeepsTheRowAndShowsTryAgain() = runTest {
        annChallenges()
        val vm = started(viewModel())
        val opened = opened(vm)
        claimOutcome = ClaimResult.Offline

        vm.accept(code)

        assertEquals(InviteNote.AcceptFailed, vm.state.value.notes[code])
        assertEquals(listOf(code), vm.rowCodes())
        assertEquals(emptyList<ShareCode>(), opened)
        assertFalse(code in vm.state.value.inFlight)
    }

    @Test
    fun declineOfflineKeepsTheRow() = runTest {
        annChallenges(invited = bob)
        val vm = started(viewModel())
        declineOutcome = WriteResult.Offline

        vm.decline(code)

        assertEquals(InviteNote.DeclineFailed, vm.state.value.notes[code])
        assertEquals(listOf(code), vm.rowCodes())
    }

    @Test
    fun acceptWhileSignedOutOpensTheDuelScreenToSignIn() = runTest {
        annChallenges()
        viewAs(null)
        val vm = started(viewModel())
        val opened = opened(vm)

        vm.accept(code)

        assertEquals(listOf(code), opened)
        assertEquals(emptyMap<ShareCode, InviteNote>(), vm.state.value.notes)
    }

    @Test
    fun declineWhileSignedOutDoesNotOpenTheDuelScreen() = runTest {
        annChallenges()
        viewAs(null)
        val vm = started(viewModel())
        val opened = opened(vm)

        vm.decline(code)

        assertEquals(emptyList<ShareCode>(), opened)
    }

    @Test
    fun callsForARowAlreadyInFlightAreIgnored() = runTest {
        annChallenges()
        val vm = started(viewModel())
        val opened = opened(vm)
        val gate = CompletableDeferred<Unit>()
        claimGate = gate

        vm.accept(code)
        assertTrue(code in vm.state.value.inFlight)
        vm.accept(code)
        vm.decline(code)
        gate.complete(Unit)

        assertEquals(listOf(code), claimCalls)
        assertEquals(emptyList<ShareCode>(), declineCalls)
        assertEquals(listOf(code), opened)
        assertFalse(code in vm.state.value.inFlight)
    }

    @Test
    fun `the pane starts on the duel that needs the player`() = runTest {
        annChallenges()
        val vm = started(viewModel())

        assertEquals(code, vm.state.value.selected)
    }

    @Test
    fun `no duels leaves nothing selected`() = runTest {
        viewAs(bob)
        val vm = started(viewModel())

        assertEquals(null, vm.state.value.selected)
    }

    @Test
    fun `selecting a duel swaps the pane and the pick holds`() = runTest {
        annChallenges()
        annChallenges(newCode = other)
        val vm = started(viewModel())
        val first = vm.state.value.selected!!
        val second = (vm.rowCodes() - first).single()

        vm.select(second)

        assertEquals(second, vm.state.value.selected)
    }

    @Test
    fun `leaving the shown duel falls to the next one`() = runTest {
        annChallenges()
        annChallenges(newCode = other)
        val vm = started(viewModel())
        val shown = vm.state.value.selected!!

        vm.leave(shown)

        assertEquals((vm.rowCodes() - shown).single(), vm.state.value.selected)
    }

    @Test
    fun `a declined duel in the pane gives way to the next`() = runTest {
        annChallenges(invited = bob)
        annChallenges(invited = bob, newCode = other)
        val vm = started(viewModel())
        val shown = vm.state.value.selected!!

        vm.decline(shown)

        assertEquals(listOf(if (shown == code) other else code), listOf(vm.state.value.selected))
    }

    @Test
    fun `Past you takes the viewer's name, You when signed out`() = runTest {
        viewAs(Player("maria", "Maria", null))
        val named = started(viewModel())
        assertEquals("Maria", named.state.value.selfName)

        viewAs(null)
        val anonymous = started(viewModel())
        assertEquals("You", anonymous.state.value.selfName)
    }

    @Test
    fun `starting New duel holds the request with its challenge and ending it clears it`() {
        val vm = viewModel()
        val sam = DuelChallenge(friendUid = "sam", friendName = "Sam", game = GameId("alpha"))
        assertEquals(null, vm.newDuel.value)

        vm.startNewDuel()
        assertEquals(NewDuelRequest(), vm.newDuel.value)

        vm.startNewDuel(sam)
        assertEquals(NewDuelRequest(sam), vm.newDuel.value)

        vm.endNewDuel()
        assertEquals(null, vm.newDuel.value)
    }

    @Test
    fun `selecting a duel ends New duel and pins the pick`() = runTest {
        annChallenges()
        annChallenges(newCode = other)
        val vm = started(viewModel())
        val second = (vm.rowCodes() - vm.state.value.selected!!).single()
        vm.startNewDuel()

        vm.select(second)

        assertEquals(null, vm.newDuel.value)
        assertEquals(second, vm.state.value.selected)
    }

    @Test
    fun `ending New duel keeps the selected duel`() = runTest {
        annChallenges()
        val vm = started(viewModel())
        vm.startNewDuel()

        vm.endNewDuel()

        assertEquals(code, vm.state.value.selected)
    }
}
