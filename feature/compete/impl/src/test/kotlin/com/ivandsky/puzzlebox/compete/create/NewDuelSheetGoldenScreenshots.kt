package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GOLDEN_PHONE = "w411dp-h891dp-420dpi"
private const val GOLDEN_TABLET = "w1280dp-h800dp-mdpi"
private const val SETTLE_MS = 2_000L
private val SHEET_MAX_WIDTH = 640.dp
private val SHEET_TOP_GAP = 8.dp
private const val SCRIM_ALPHA = 0.32f

/** The New duel sheet's states from the contract, phone and tablet, light and dark. Golden in `snapshots/new-duel/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class NewDuelSheetGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinWithBrands() {
        startKoin { modules(newDuelModule()) }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    @Test fun `friend phone light`() = golden("friend", friend(), tablet = false, dark = false)
    @Test fun `friend phone dark`() = golden("friend", friend(), tablet = false, dark = true)
    @Test fun `yourself quick phone light`() = golden("yourself-quick", quick(), tablet = false, dark = false)
    @Test fun `yourself quick phone dark`() = golden("yourself-quick", quick(), tablet = false, dark = true)
    @Test fun `yourself custom phone light`() = golden("yourself-custom", custom(), tablet = false, dark = false, customize = true)
    @Test fun `yourself custom phone dark`() = golden("yourself-custom", custom(), tablet = false, dark = true, customize = true)
    @Test fun `few solved phone light`() = golden("few-solved", fewSolved(), tablet = false, dark = false, customize = true)
    @Test fun `few solved phone dark`() = golden("few-solved", fewSolved(), tablet = false, dark = true, customize = true)
    @Test fun `none solved phone light`() = golden("none-solved", noneSolved(), tablet = false, dark = false, customize = true)
    @Test fun `none solved phone dark`() = golden("none-solved", noneSolved(), tablet = false, dark = true, customize = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friend tablet light`() = golden("friend", friend(), tablet = true, dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friend tablet dark`() = golden("friend", friend(), tablet = true, dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `yourself quick tablet light`() = golden("yourself-quick", quick(), tablet = true, dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `yourself quick tablet dark`() = golden("yourself-quick", quick(), tablet = true, dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `yourself custom tablet light`() = golden("yourself-custom", custom(), tablet = true, dark = false, customize = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `yourself custom tablet dark`() = golden("yourself-custom", custom(), tablet = true, dark = true, customize = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `few solved tablet light`() = golden("few-solved", fewSolved(), tablet = true, dark = false, customize = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `few solved tablet dark`() = golden("few-solved", fewSolved(), tablet = true, dark = true, customize = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `none solved tablet light`() = golden("none-solved", noneSolved(), tablet = true, dark = false, customize = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `none solved tablet dark`() = golden("none-solved", noneSolved(), tablet = true, dark = true, customize = true)

    private fun golden(name: String, state: NewDuelState, tablet: Boolean, dark: Boolean, customize: Boolean = false) {
        rule.showGolden(dark) {
            SheetBackdrop(tablet) { NewDuelContent(state, NO_ACTIONS) }
        }
        if (customize) {
            rule.mainClock.advanceTimeBy(SETTLE_MS)
            rule.onNodeWithTag("duel.preset.custom").performClick()
        }
        rule.settleAndCapture("new-duel", "new-duel-sheet-$name", if (tablet) "tablet" else "phone", dark)
    }
}

/** ModalBottomSheet's chrome without its window: a scrim, the sheet's surface and drag handle, bottom-aligned, at most 640 dp wide on a tablet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetBackdrop(tablet: Boolean, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = (if (tablet) Modifier.widthIn(max = SHEET_MAX_WIDTH) else Modifier).fillMaxWidth().heightIn(max = maxHeight - SHEET_TOP_GAP),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = BottomSheetDefaults.ExpandedShape,
        ) {
            Column {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
                content()
            }
        }
    }
}

private fun ComposeContentTestRule.showGolden(dark: Boolean, content: @Composable () -> Unit) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    mainClock.autoAdvance = false
    setContent {
        val config = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides config) {
            PuzzleBoxTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }
}

@OptIn(ExperimentalRoborazziApi::class)
private fun ComposeContentTestRule.settleAndCapture(group: String, name: String, device: String, dark: Boolean) {
    mainClock.advanceTimeBy(SETTLE_MS)
    val file = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/$group/$name-$device-${if (dark) "dark" else "light"}.png")
        .canonicalFile
        .also { checkNotNull(it.parentFile).mkdirs() }
    captureScreenRoboImage(file.absolutePath)
}
