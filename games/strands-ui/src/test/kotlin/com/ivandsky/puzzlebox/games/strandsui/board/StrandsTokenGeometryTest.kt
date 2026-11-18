package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The painter, the connector clipper and the layer's ring room read one geometry, so they cannot drift apart. */
class StrandsTokenGeometryTest {

    private val densities = listOf(1f, 2f, 2.625f, 3f, 3.5f)

    @Test
    fun `the layer's ring room is a fraction of the face side, rounded up, at 48 dp and 360 dp pitches`() {
        for (pitchDp in listOf(48f, 360f)) for (density in densities) {
            val face = (pitchDp * density * StrandsOutlinedSpec.TokenFraction).toInt()
            val exact = face * StrandsBoardSpec.RingRoomFraction
            val room = ringRoomPx(face)
            assertTrue("room $room < $exact at $pitchDp dp x $density", room >= exact)
            assertTrue("room $room more than a pixel past $exact", room < exact + 1f)
        }
    }

    @Test
    fun `the face is centred on the token and carries the one rounded corner`() {
        val token = tokenGeometry(Offset(100f, 50f), half = 20f, scale = 0.5f)
        assertEquals(10f, token.face.width / 2f, 0f)
        assertEquals(Offset(100f, 50f), token.face.center)
        assertEquals(token.face.width * StrandsBoardSpec.TokenCornerFraction, token.cornerRadius.x, 0f)
    }
}
