package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The gate is the one choke point for haptics: it passes calls while enabled and drops them while not. */
class GatedHapticFeedbackTest {
    private val delegate = RecordingHaptics()
    private var enabled = true
    private val gate = GatedHapticFeedback(delegate) { enabled }

    @Test
    fun `enabled calls reach the delegate`() {
        gate.performHapticFeedback(HapticFeedbackType.Confirm)
        gate.performHapticFeedback(HapticFeedbackType.LongPress)

        assertEquals(listOf(HapticFeedbackType.Confirm, HapticFeedbackType.LongPress), delegate.calls)
    }

    @Test
    fun `disabled calls are dropped`() {
        enabled = false
        gate.performHapticFeedback(HapticFeedbackType.Confirm)

        assertEquals(emptyList<HapticFeedbackType>(), delegate.calls)
    }

    @Test
    fun `a flip applies to the next call`() {
        enabled = false
        gate.performHapticFeedback(HapticFeedbackType.Confirm)
        enabled = true
        gate.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        assertEquals(listOf(HapticFeedbackType.TextHandleMove), delegate.calls)
    }
}

private class RecordingHaptics : HapticFeedback {
    val calls = ArrayList<HapticFeedbackType>()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        calls += hapticFeedbackType
    }
}
