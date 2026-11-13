package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreVisibilityRepositoryTest {

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
