package com.ivandsky.puzzlebox.app.nav

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.gameui.LocalHintCooldown
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** F2 / A20: every entry's content sees a five-second Hint cooldown while HintParity is on, and none while off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HintCooldownProviderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data object Board : NavKey

    private var seen: Duration? = Duration.INFINITE

    private val provider: (NavKey) -> NavEntry<NavKey> = { key -> NavEntry(key) { seen = LocalHintCooldown.current } }

    private fun show(flags: ReleaseFlags) {
        val entry = provider.withHintCooldown(flags)(Board)
        composeRule.setContent { entry.Content() }
        composeRule.waitForIdle()
    }

    @Test
    fun `HintParity on gives each entry a five-second cooldown`() {
        show(FakeReleaseFlags(ReleaseFlag.HintParity))
        assertEquals(5.seconds, seen)
    }

    @Test
    fun `HintParity off gives no cooldown`() {
        show(FakeReleaseFlags())
        assertNull(seen)
    }
}
