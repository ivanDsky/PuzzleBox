package com.ivandsky.puzzlebox.compete.create.lineup

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val NARROW = "w360dp-h800dp-420dpi"
private const val PHONE = "w412dp-h915dp-420dpi"
private const val TABLET = "w1280dp-h800dp-mdpi"
private val MIN_TARGET = 48.dp
private val PANE_HEIGHT_SLACK = 120.dp

/** The add sheet's layout at a narrow phone, a phone and a tablet: one column below the app's two-pane width, two panes on it (A7). Renders go to `snapshots/add-puzzle-sheet/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
class AddPuzzleSheetLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun stopKoinAgain() = stopKoin()

    private var editor by mutableStateOf(LineupEditor.opening(GAMES))

    private val rows = listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID)

    private fun open(render: String, filtersOpen: Boolean = true) {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        startKoin { modules(module { single { newDuelBrands() }; viewModel { AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP) } }) }
        rule.setContent {
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    LineupPanel(editor, GAMES, includeFriends = false, onEdit = { edit -> editor = edit(editor) })
                }
            }
        }
        rule.onNodeWithTag("duel.lineup.add").performClick()
        rule.waitForIdle()
        if (filtersOpen) rule.openFilters()
        capture(render)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        val file = File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/add-puzzle-sheet/$name.png").canonicalFile
        checkNotNull(file.parentFile).mkdirs()
        captureScreenRoboImage(file.absolutePath)
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun touch(tag: String): Rect = rule.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode().touchBoundsInRoot

    private val tile = "duel.lineup.sheet.game.queens"
    private val lastTile = "duel.lineup.sheet.game.strands"
    private val chip = "duel.lineup.chip.new"
    private val lastChip = "duel.lineup.chip.game.strands"
    private val search = "duel.lineup.search"

    /** The phone's sheet opens half-expanded and its results sit under the fold, so there only what is above it is measured. */
    private fun controls(resultsVisible: Boolean) = buildList {
        GAMES.forEach { add("duel.lineup.sheet.game.${it.id.value}"); add("duel.lineup.chip.game.${it.id.value}") }
        listOf("new", "notplayed", "community", "friends", "pastdays", "mine").forEach { add("duel.lineup.chip.$it") }
        add(search)
        add("duel.lineup.filter")
        add("duel.lineup.filter.clear")
        add("duel.lineup.search.paste")
        if (resultsVisible) rows.forEach { add("duel.lineup.pick.add.${it.entry.ref.wire}") }
    }

    private fun assertEveryControlIsAtLeast48dp(resultsVisible: Boolean = true) {
        val minPx = with(rule.density) { MIN_TARGET.toPx() }
        for (tag in controls(resultsVisible)) {
            val target = touch(tag)
            assertTrue("$tag is ${target.width}x${target.height} px", target.width >= minPx - 1 && target.height >= minPx - 1)
        }
    }

    private fun assertSingleColumn() {
        assertTrue("the field is under the tiles", bounds(search).top >= bounds(lastTile).bottom)
        assertTrue("the chips are under the field", bounds(chip).top >= bounds(search).bottom)
        assertTrue("the chips share the tiles' left edge", abs(bounds(chip).left - bounds(tile).left) < 1f)
        assertTrue("the field spans the sheet's width", bounds(search).width > bounds(tile).width)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun `a narrow phone keeps the single column`() {
        open("add-sheet-360")

        assertSingleColumn()
        assertEveryControlIsAtLeast48dp(resultsVisible = false)
    }

    @Test
    fun `a phone keeps the single column, tiles then field then chips, with every control at least 48 dp`() {
        open("add-sheet-412x915")

        assertSingleColumn()
        assertEveryControlIsAtLeast48dp(resultsVisible = false)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `a tablet splits the sheet, the tiles on the left, the field, its filter button, the chips and results on the right`() {
        open("add-sheet-1280x800")

        val left = listOf(bounds(tile), bounds(lastTile))
        val right = listOf(bounds(search), bounds("duel.lineup.filter"), bounds(chip), bounds(lastChip), bounds("duel.lineup.pick.add.${CROWN_JEWELS.entry.ref.wire}"))
        assertTrue("every left control ends before the right pane starts", left.maxOf { it.right } <= right.minOf { it.left })
        assertTrue("the chips open under the field", bounds(search).top < bounds(chip).top)
        assertTrue("the sheet is wider than a phone sheet", bounds("duel.lineup.sheet").width > with(rule.density) { 640.dp.toPx() })
        assertEveryControlIsAtLeast48dp()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `on a tablet neither pane leaves a large empty space under the other`() {
        open("add-sheet-1280x800-filters-closed", filtersOpen = false)

        val leftBottom = bounds(lastTile).bottom
        val rightBottom = bounds("duel.lineup.pick.add.${MY_GRID.entry.ref.wire}").bottom
        val slack = with(rule.density) { PANE_HEIGHT_SLACK.toPx() }
        assertTrue("panes end $leftBottom and $rightBottom", abs(leftBottom - rightBottom) <= slack)
    }
}
