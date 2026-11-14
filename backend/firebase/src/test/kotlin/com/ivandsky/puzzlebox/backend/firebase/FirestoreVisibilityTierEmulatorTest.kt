package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.visibility.TierLookup
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FirestoreVisibilityRepository.tierOf], the one-shot read of another player's tier, against the Firestore emulator
 * under the deployed rules. Skipped when the emulators are not up (see [FirestoreReplayDocStoreEmulatorTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreVisibilityTierEmulatorTest {

    private val repository = FirestoreVisibilityRepository(MutableStateFlow(null))

    @Before
    fun freshEmulator() = EmulatorBackend.startClean()

    @Test
    fun `a tier another player set reads back as that tier`() = EmulatorBackend.blocking {
        val owner = EmulatorBackend.signInAsNewUser()
        EmulatorBackend.setTier(owner, VisibilityTier.Duel)
        EmulatorBackend.signInAsNewUser()

        assertEquals(TierLookup.Found(VisibilityTier.Duel), repository.tierOf(owner))
    }

    @Test
    fun `a player who never chose reads as Board`() = EmulatorBackend.blocking {
        val reader = EmulatorBackend.signInAsNewUser()
        val neverChose = "no-such-player"

        assertEquals(TierLookup.Found(VisibilityTier.Board), repository.tierOf(neverChose))
        assertEquals(TierLookup.Found(VisibilityTier.Board), repository.tierOf(reader))
    }

    @Test
    fun `a read the rules refuse is Failed`() = EmulatorBackend.blocking {
        val owner = EmulatorBackend.signInAsNewUser()
        EmulatorBackend.setTier(owner, VisibilityTier.Hidden)
        EmulatorBackend.signOut()

        assertEquals(TierLookup.Failed, repository.tierOf(owner))
    }
}
