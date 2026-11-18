package com.ivandsky.puzzlebox.compete.create.screen

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.compete.create.NO_ACTIONS
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.custom
import com.ivandsky.puzzlebox.compete.create.friend
import com.ivandsky.puzzlebox.compete.create.lineup.AddPuzzleViewModel
import com.ivandsky.puzzlebox.compete.create.lineup.CROWN_JEWELS
import com.ivandsky.puzzlebox.compete.create.lineup.CROWN_TANGO
import com.ivandsky.puzzlebox.compete.create.lineup.MY_GRID
import com.ivandsky.puzzlebox.compete.create.lineup.NO_LOOKUP
import com.ivandsky.puzzlebox.compete.create.lineup.OLD_CROWN
import com.ivandsky.puzzlebox.compete.create.lineup.PAST_YOU_PICKER
import com.ivandsky.puzzlebox.compete.create.lineup.PuzzlePickerPage
import com.ivandsky.puzzlebox.compete.create.lineup.pickerOf
import com.ivandsky.puzzlebox.compete.create.newDuelModule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GOLDEN_PHONE = "w411dp-h891dp-420dpi"
private const val GOLDEN_TABLET = "w1280dp-h800dp-mdpi"
private const val SETTLE_MS = 2_000L

/** The Add puzzle page of the New duel screen, for a friend and for Past you, phone and tablet. Golden in `snapshots/new-duel-picker/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class NewDuelPickerGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinWithRows() {
        startKoin {
            modules(newDuelModule(), module { viewModel { AddPuzzleViewModel(pickerOf(listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID)), NO_LOOKUP) } })
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    @Test fun `friend phone light`() = golden("friend", adding(friend()), tablet = false, dark = false)
    @Test fun `friend phone dark`() = golden("friend", adding(friend()), tablet = false, dark = true)
    @Test fun `friend filters phone light`() = golden("friend-filters", adding(friend()), tablet = false, dark = false, filters = true)
    @Test fun `past you phone light`() = golden("past-you", adding(custom()), tablet = false, dark = false)
    @Test fun `past you phone dark`() = golden("past-you", adding(custom()), tablet = false, dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `friend tablet light`() = golden("friend", adding(friend()), tablet = true, dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `past you tablet dark`() = golden("past-you", adding(custom()), tablet = true, dark = true)

    private fun adding(state: NewDuelState) = state.copy(lineup = state.lineup.openAdd())

    private fun golden(name: String, state: NewDuelState, tablet: Boolean, dark: Boolean, filters: Boolean = false) {
        rule.showGolden(dark) {
            NewDuelScreen(state, NO_ACTIONS, onBack = {}, pane = tablet) { picker ->
                PuzzlePickerPage(
                    picker = picker,
                    onPick = {},
                    viewModel = if (picker.pastYou) koinViewModel<AddPuzzleViewModel>(qualifier = PAST_YOU_PICKER) else koinViewModel<AddPuzzleViewModel>(),
                )
            }
        }
        if (filters) {
            rule.mainClock.advanceTimeBy(SETTLE_MS)
            rule.onNodeWithTag("duel.lineup.filter").performClick()
        }
        rule.settleAndCapture("new-duel-picker", "new-duel-picker-$name", if (tablet) "tablet" else "phone", dark)
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
