package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.play.CommunityPanes
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.ShelvesListPaneWidth
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The See all page, light and dark: Newest, a game's page (no game filter), a set difficulty filter (underlined), a
 * search with nothing matching, and the loading state. The phone fills the screen; the tablet shows it in the 600dp list
 * pane beside the empty detail pane. Goldens in `snapshots/community-shelves/`; untracked review captures, never a gate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class SeeAllGoldenScreenshotsPhone : SeeAllGoldenScreenshots(tablet = false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class SeeAllGoldenScreenshotsTablet : SeeAllGoldenScreenshots(tablet = true)

abstract class SeeAllGoldenScreenshots(private val tablet: Boolean) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val actions = SeeAllActions({}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test fun `newest light`() = capture("seeall-newest", SeeAllSampleStates.ready(), dark = false)
    @Test fun `newest dark`() = capture("seeall-newest", SeeAllSampleStates.ready(), dark = true)

    @Test fun `game light`() = capture("seeall-game", SeeAllSampleStates.ready(SeeAllScope.Game(ALPHA)), dark = false)
    @Test fun `game dark`() = capture("seeall-game", SeeAllSampleStates.ready(SeeAllScope.Game(ALPHA)), dark = true)

    @Test fun `filtered light`() = capture("seeall-filtered", SeeAllSampleStates.ready(difficulty = DifficultyChoice.Medium, game = ALPHA), dark = false)
    @Test fun `filtered dark`() = capture("seeall-filtered", SeeAllSampleStates.ready(difficulty = DifficultyChoice.Medium, game = ALPHA), dark = true)

    @Test fun `nomatch light`() = capture("seeall-nomatch", SeeAllSampleStates.noMatch(), dark = false)
    @Test fun `nomatch dark`() = capture("seeall-nomatch", SeeAllSampleStates.noMatch(), dark = true)

    @Test fun `loading light`() = capture("seeall-loading", SeeAllUiState(scope = SeeAllScope.Friends), dark = false)

    private fun capture(name: String, state: SeeAllUiState, dark: Boolean) {
        rule.showGolden(dark) {
            if (tablet) {
                CommunityPanes(
                    default = { null },
                    list = { size, _ -> SeeAllScreen(state, actions, size) },
                    empty = { Text("Pick a puzzle to see it here") },
                    detail = { _, _ -> },
                    listPaneWidth = ShelvesListPaneWidth,
                )
            } else {
                SeeAllScreen(state, actions, Modifier.fillMaxSize())
            }
        }
        rule.settleAndCapture("community-shelves", name, if (tablet) "tablet" else "phone", dark)
    }
}
