package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The painter, the connector clipper and the layer's lip room read one geometry, so they cannot drift apart. */
class StrandsTokenGeometryTest {

    private val densities = listOf(1f, 2f, 2.625f, 3f, 3.5f)

    @Test
    fun `the layer's lip room reaches at least as far as the lip paints, at 48 dp and 360 dp pitches`() {
        for (pitchDp in listOf(48f, 360f)) for (density in densities) {
            val face = (pitchDp * density * StrandsBoardSpec.TokenFraction).toInt()
            val drop = tokenGeometry(Offset(face / 2f, face / 2f), face / 2f).drop
            val room = lipRoomPx(face)
            assertTrue("room $room < drop $drop at $pitchDp dp x $density", room >= drop)
            assertTrue("room $room more than a pixel past drop $drop", room < drop + 1f)
        }
    }

    @Test
    fun `the lip is the face moved down by the drop, both on one corner`() {
        val token = tokenGeometry(Offset(100f, 50f), half = 20f, scale = 0.5f)
        assertEquals(token.face.translate(0f, token.drop), token.lip)
        assertEquals(10f, token.face.width / 2f, 0f)
        assertEquals(Offset(100f, 50f), token.face.center)
        assertEquals(token.face.width * StrandsBoardSpec.TokenCornerFraction, token.cornerRadius.x, 0f)
    }
}
