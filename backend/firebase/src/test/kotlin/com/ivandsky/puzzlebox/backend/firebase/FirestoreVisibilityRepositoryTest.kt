package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirestoreVisibilityRepositoryTest {

    private val me = MutableStateFlow<Identity?>(Identity(uid = "me", displayName = "Me", photoUrl = null))

    @Test
    fun `a pick writes the signed-in player's tier doc`() = runTest {
        val written = mutableMapOf<String, Map<String, Any>>()
        val repository = FirestoreVisibilityRepository(me, write = { uid, fields -> written[uid] = fields })

        assertEquals(true, repository.setOwnTier(VisibilityTier.Duel))
        assertEquals(mapOf("me" to encodeTierDoc(VisibilityTier.Duel)), written)
    }

    @Test
    fun `a signed-out pick is refused without a write`() = runTest {
        var writes = 0
        val repository = FirestoreVisibilityRepository(MutableStateFlow(null), write = { _, _ -> writes++ })

        assertEquals(false, repository.setOwnTier(VisibilityTier.Duel))
        assertEquals(0, writes)
    }

    @Test
    fun `a write the rules refuse is a refused pick, not an error`() = runTest {
        val repository = FirestoreVisibilityRepository(me, write = { _, _ -> error("PERMISSION_DENIED") })

        assertEquals(false, repository.setOwnTier(VisibilityTier.Duel))
    }

    @Test
    fun `a write that never answers counts as written once the timeout passes`() = runTest {
        val repository = FirestoreVisibilityRepository(me, timeout = 1.seconds, write = { _, _ -> awaitCancellation() })

        assertEquals(true, repository.setOwnTier(VisibilityTier.Duel))
    }

    @Test
    fun `a cancelled pick stays cancelled`() = runTest {
        val repository = FirestoreVisibilityRepository(me, write = { _, _ -> awaitCancellation() })
        var returned = false
        val pick = launch {
            repository.setOwnTier(VisibilityTier.Duel)
            returned = true
        }
        runCurrent()

        pick.cancel()
        runCurrent()

        assertFalse(returned)
    }

    @Test
    fun `every tier encodes to its storage name with version 1 and decodes back`() {
        VisibilityTier.entries.forEach { tier ->
            val doc = encodeTierDoc(tier)
            assertEquals(mapOf("tier" to tier.storageName, "v" to 1L), doc)
            assertEquals(tier, decodeTier(doc))
        }
    }

    @Test
    fun `storage names are hidden board duel replays`() {
        assertEquals(
            listOf("hidden", "board", "duel", "replays"),
            VisibilityTier.entries.map { encodeTierDoc(it).getValue("tier") },
        )
    }

    @Test
    fun `a missing doc decodes to Board`() {
        assertEquals(VisibilityTier.Board, decodeTier(null))
    }

    @Test
    fun `an unknown, missing or mistyped tier value decodes to Board`() {
        assertEquals(VisibilityTier.Board, decodeTier(mapOf("tier" to "platinum")))
        assertEquals(VisibilityTier.Board, decodeTier(mapOf("v" to 1L)))
        assertEquals(VisibilityTier.Board, decodeTier(mapOf("tier" to 3L)))
    }
}
