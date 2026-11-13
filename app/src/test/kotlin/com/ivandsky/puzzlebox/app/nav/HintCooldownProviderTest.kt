package com.ivandsky.puzzlebox.app.nav

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.gameui.LocalHintCooldown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** F2: every entry's content sees a five-second Hint cooldown. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HintCooldownProviderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data object Board : NavKey

    private var seen: Duration? = Duration.INFINITE

    private val provider: (NavKey) -> NavEntry<NavKey> = { key -> NavEntry(key) { seen = LocalHintCooldown.current } }

    @Test
    fun `each entry sees a five-second cooldown`() {
        val entry = provider.withHintCooldown()(Board)
        composeRule.setContent { entry.Content() }
        composeRule.waitForIdle()
        assertEquals(5.seconds, seen)
    }
}
