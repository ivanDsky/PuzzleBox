package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// Enabling a button lerps lip colour -> disabled lip; a black-transparent target would
// drag the lip through muddy dark mid-frames, so it keeps its hue at alpha 0.
class GameButtonDisabledColorsTest {
    private val scheme = lightColorScheme()
    private val enabled = GameButtonColors(fill = Color(0xFF2EB5A6), content = Color.White, lip = Color(0xFF1A7F74))

    @Test
    fun `an enabled button keeps its own colours`() {
        assertEquals(enabled, gameButtonTarget(enabled, enabled = true, scheme))
    }

    @Test
    fun `a disabled lip fades out in its own hue not through black`() {
        val lip = gameButtonTarget(enabled, enabled = false, scheme).lip
        assertEquals(0f, lip.alpha, 0f)
        assertEquals(enabled.lip.copy(alpha = 0f), lip)
        assertNotEquals(Color.Transparent, lip)
    }

    @Test
    fun `a disabled face is the muted onSurface wash`() {
        val target = gameButtonTarget(enabled, enabled = false, scheme)
        assertEquals(scheme.onSurface.copy(alpha = 0.12f), target.fill)
        assertEquals(scheme.onSurface.copy(alpha = 0.38f), target.content)
    }
}
