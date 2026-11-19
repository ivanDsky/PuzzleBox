package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A dialog and a bottom sheet compose in their own window, which brings its own platform haptics. The app's gate must
 * still be what a buzz inside them reaches, so Vibration off stays silent there too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WindowHapticsGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = ArrayList<HapticFeedbackType>()
    private val platform = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            calls += hapticFeedbackType
        }
    }
    private val vibrationOn = mutableStateOf(true)
    private lateinit var insideWindow: HapticFeedback

    private fun show(window: @Composable (@Composable () -> Unit) -> Unit) {
        val gated = GatedHapticFeedback(platform) { vibrationOn.value }
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides gated) {
                window { insideWindow = LocalHapticFeedback.current }
            }
        }
        composeRule.waitForIdle()
    }

    private val sheet: @Composable (@Composable () -> Unit) -> Unit = { body ->
        AppModalBottomSheet(onDismissRequest = {}) { body() }
    }

    private val dialog: @Composable (@Composable () -> Unit) -> Unit = { body ->
        AppDialog(title = "Delete board?", onDismissRequest = {}, content = { body() })
    }

    @Test
    fun `a sheet with Vibration off plays nothing`() {
        vibrationOn.value = false
        show(sheet)

        insideWindow.performHapticFeedback(HapticFeedbackType.Confirm)

        assertEquals(emptyList<HapticFeedbackType>(), calls)
    }

    @Test
    fun `a sheet with Vibration on plays the buzz`() {
        show(sheet)

        insideWindow.performHapticFeedback(HapticFeedbackType.Confirm)

        assertEquals(listOf(HapticFeedbackType.Confirm), calls)
    }

    @Test
    fun `a dialog with Vibration off plays nothing`() {
        vibrationOn.value = false
        show(dialog)

        insideWindow.performHapticFeedback(HapticFeedbackType.Confirm)

        assertEquals(emptyList<HapticFeedbackType>(), calls)
    }

    @Test
    fun `a dialog with Vibration on plays the buzz`() {
        show(dialog)

        insideWindow.performHapticFeedback(HapticFeedbackType.Confirm)

        assertEquals(listOf(HapticFeedbackType.Confirm), calls)
    }
}
