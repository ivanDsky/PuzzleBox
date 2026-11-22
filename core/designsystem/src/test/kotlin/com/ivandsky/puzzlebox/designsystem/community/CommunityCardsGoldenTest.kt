package com.ivandsky.puzzlebox.designsystem.community

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of the community cards, light and dark, at 360dp; the longest-copy sheets also at 320dp
 * with font scale 1.3. Rendered with `-Proborazzi.record=true` (untracked review captures in
 * `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-420dpi")
class CommunityCardsGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shelf cards with every badge`() = capture("ShelfCard_badges", dark = false) { ShelfCardBadgesPreview() }

    @Test
    fun `shelf cards with every badge in the dark theme`() = capture("ShelfCard_badges_dark", dark = true) { ShelfCardBadgesPreview() }

    @Test
    fun `shelf card with the longest copy`() = capture("ShelfCard_longest", dark = false) { ShelfCardLongestPreview() }

    @Test
    fun `shelf card with the longest copy in the dark theme`() = capture("ShelfCard_longest_dark", dark = true) { ShelfCardLongestPreview() }

    @Test
    fun `shelf card with the longest copy at 320dp and font scale 1 point 3`() =
        capture("ShelfCard_longest_320_font13", dark = false, widthDp = 320, fontScale = 1.3f) { ShelfCardLongestPreview() }

    @Test
    fun `spotlight cards`() = capture("SpotlightCard", dark = false) { SpotlightCardPreview() }

    @Test
    fun `spotlight cards in the dark theme`() = capture("SpotlightCard_dark", dark = true) { SpotlightCardPreview() }

    @Test
    fun `spotlight card with the longest copy at 320dp and font scale 1 point 3`() =
        capture("SpotlightCard_longest_320_font13", dark = false, widthDp = 320, fontScale = 1.3f) { SpotlightCardLongestPreview() }

    @Test
    fun `entry tiles`() = capture("EntryTile", dark = false) { EntryTilesPreview() }

    @Test
    fun `entry tiles in the dark theme`() = capture("EntryTile_dark", dark = true) { EntryTilesPreview() }

    @Test
    fun `entry tiles with the longest copy at 320dp and font scale 1 point 3`() =
        capture("EntryTile_longest_320_font13", dark = false, widthDp = 320, fontScale = 1.3f) { EntryTilesLongestPreview() }

    @Test
    fun `make puzzle cards`() = capture("MakePuzzleCard", dark = false) { MakePuzzleCardsPreview() }

    @Test
    fun `make puzzle cards in the dark theme`() = capture("MakePuzzleCard_dark", dark = true) { MakePuzzleCardsPreview() }

    private fun capture(name: String, dark: Boolean, widthDp: Int = 360, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                Box(Modifier.width(widthDp.dp)) { content() }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/$name.png")
                .canonicalFile
                .absolutePath,
        )
    }
}
