package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Variant table: LinkedIn's daily sizes as data (ADR-0005); unknown ids are never guessed. */
class ZipVariantsTest {

    @Test
    fun `table carries the daily sizes`() {
        assertEquals(setOf("zip/n5", "zip/n6", "zip/n7"), ZipVariants.table.keys.map { it.value }.toSet())
        ZipVariants.table.values.forEach { variant ->
            assertEquals(variant.id.value.removePrefix("zip/n").toInt(), variant.size)
            assertTrue(variant.features.isEmpty()) // anti-speculation: empty until a consumer exists
        }
    }

    @Test
    fun `byId returns null for unknown regimes`() {
        assertNull(ZipVariants.byId(VariantId("zip/n4")))
        assertNull(ZipVariants.byId(VariantId("zip/n8")))
        assertNull(ZipVariants.byId(VariantId("queens/6")))
    }

    @Test
    fun `all mirrors table`() {
        assertEquals(ZipVariants.table.values.toSet(), ZipVariants.all.toSet())
    }
}
