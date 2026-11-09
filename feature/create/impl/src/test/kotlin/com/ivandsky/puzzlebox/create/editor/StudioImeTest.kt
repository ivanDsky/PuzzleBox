package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Device check R3: with the keyboard open the Studio keeps every board row and the focused
 * field reachable. The keyboard is the IME inset dispatched to the compose root (Robolectric
 * draws no keyboard; the goldens paint its band). The field sits in the tool strip, as the
 * Strands word field does. Goldens (light only) in `snapshots/studio/keyboard-<device>-light.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StudioImeTestPhone : StudioImeTest(PlayLayout.SinglePane, "phone", keyboard = 320.dp)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class StudioImeTestTablet : StudioImeTest(PlayLayout.TwoPane, "tablet", keyboard = 440.dp)

abstract class StudioImeTest(private val layout: PlayLayout, private val device: String, private val keyboard: Dp) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View

    @Test
    fun `the focused field stays above the keyboard and the board keeps its size`() {
        assertFieldAboveKeyboard(slides = true)
    }

    @Test
    fun `the focused field stays above a keyboard that arrives in one step`() {
        // API 26-29 and animations off: the inset jumps from none to full in one frame.
        assertFieldAboveKeyboard(slides = false)
    }

    private fun assertFieldAboveKeyboard(slides: Boolean) {
        show()
        val grid = rule.onNodeWithTag(GRID).getUnclippedBoundsInRoot()
        rule.onNodeWithTag(FIELD).performClick()
        openKeyboard(keyboard, slides)

        val field = rule.onNodeWithTag(FIELD).getUnclippedBoundsInRoot()
        assertTrue("field bottom ${field.bottom} under the keyboard top ${keyboardTop()}", field.bottom <= keyboardTop())
        assertTrue("field top ${field.top} under the top bar", field.top >= topBarBottom())
        val after = rule.onNodeWithTag(GRID).getUnclippedBoundsInRoot()
        assertEquals("the board keeps its width", (grid.right - grid.left).value, (after.right - after.left).value, 0.5f)
        assertEquals("the board keeps its height", (grid.bottom - grid.top).value, (after.bottom - after.top).value, 0.5f)
    }

    @Test
    fun `every board row scrolls into reach above the keyboard`() {
        show()
        rule.onNodeWithTag(FIELD).performClick()
        openKeyboard(keyboard)

        rule.onNodeWithTag(row(ROWS - 1)).performScrollTo()
        val last = rule.onNodeWithTag(row(ROWS - 1)).getUnclippedBoundsInRoot()
        assertTrue("last row bottom ${last.bottom} under the keyboard top ${keyboardTop()}", last.bottom <= keyboardTop() + 1.dp)
        rule.onNodeWithTag(row(0)).performScrollTo()
        val first = rule.onNodeWithTag(row(0)).getUnclippedBoundsInRoot()
        assertTrue("first row top ${first.top} under the top bar", first.top >= topBarBottom() - 1.dp)
    }

    @Test
    fun `chrome under the Studio counts against the keyboard, so the Studio pads no more than it covers`() {
        show(bottomChrome = CHROME)
        rule.onNodeWithTag(FIELD).performClick()
        openKeyboard(keyboard)

        // The scrolling viewport (phone column, tablet board pane) ends right at the keyboard's top.
        val viewport = rule.onNodeWithTag("create.studio.keyboardScroll").getUnclippedBoundsInRoot()
        assertEquals(keyboardTop().value, viewport.bottom.value, 1f)
        if (layout == PlayLayout.TwoPane) {
            // The side pane too, its own padding above the keyboard.
            val pane = rule.onNodeWithTag("create.studio.sidePane").getUnclippedBoundsInRoot()
            assertEquals(keyboardTop().value, (pane.bottom + Spacing.md).value, 1f)
        }
    }

    @Test
    fun `the window resizes for the keyboard while the Studio shows and pans no more`() {
        assertWindowModeRestored(from = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
    }

    @Test
    fun `a window relaunched by a config change gets its unspecified mode back on every leave`() {
        // Rotation, dark mode or font scale relaunch the activity: the new window holds the
        // manifest's mode 0, without the forward bit a fresh launch ORs in.
        assertWindowModeRestored(from = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED)
    }

    private fun assertWindowModeRestored(from: Int) {
        var shown by mutableStateOf(false)
        val window = rule.activity.window
        rule.runOnUiThread { window.attributes = window.attributes.apply { softInputMode = from } }
        setContent { if (shown) Studio() }
        repeat(2) { visit ->
            shown = true
            rule.waitForIdle()
            assertEquals("visit $visit", WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, window.attributes.softInputMode and ADJUST)
            shown = false
            rule.waitForIdle()
            assertEquals("visit $visit", from, window.attributes.softInputMode)
        }
    }

    @Test
    fun `keyboard open light`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var keyboardBand by mutableStateOf(0.dp)
        setContent {
            Box(Modifier.fillMaxSize()) {
                Studio()
                // Robolectric draws no keyboard: a band where it would sit.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(keyboardBand)
                        .background(MaterialTheme.colorScheme.inverseSurface),
                )
            }
        }
        rule.onNodeWithTag(FIELD).performClick()
        openKeyboard(keyboard)
        keyboardBand = keyboard
        rule.waitForIdle()
        captureScreenRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/studio/keyboard-$device-light.png")
                .canonicalFile
                .also { checkNotNull(it.parentFile).mkdirs() }
                .absolutePath,
        )
    }

    private fun show(bottomChrome: Dp = 0.dp) {
        setContent { Box(Modifier.fillMaxSize().padding(bottom = bottomChrome)) { Studio() } }
    }

    private fun setContent(content: @Composable () -> Unit) {
        rule.setContent {
            view = LocalView.current
            PuzzleBoxTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

    /**
     * The keyboard [height] from the window's bottom, as the system reports it. A keyboard that
     * [slides] in reports a first sliver, then the rest; one that does not jumps straight to full.
     */
    private fun openKeyboard(height: Dp, slides: Boolean = true) {
        (if (slides) listOf(1.dp, height) else listOf(height)).forEach { step ->
            val px = with(rule.density) { step.roundToPx() }
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            rule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
            rule.waitForIdle()
        }
    }

    private fun keyboardTop(): Dp = rule.onRoot().getUnclippedBoundsInRoot().bottom - keyboard

    private fun topBarBottom(): Dp = rule.onNodeWithTag("create.editor.back").getUnclippedBoundsInRoot().bottom

    @Composable
    private fun Studio() {
        var word by remember { mutableStateOf("") }
        StudioScreen(
            title = "Rainbow",
            subtitle = "Strands 8×6",
            saving = false,
            verdict = Verdict.Checking,
            layout = layout,
            onBack = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            board = { boardModifier -> Board(boardModifier) },
            toolStrip = { stripModifier ->
                Column(stripModifier) {
                    ToolStrip(
                        canUndo = true,
                        canRedo = false,
                        onUndo = {},
                        onRedo = {},
                        sizes = listOf(VariantId("strands/8x6")),
                        size = VariantId("strands/8x6"),
                        sizeLabel = { "8×6" },
                        onResize = {},
                        onClear = {},
                        hint = { Text("Drag across letters to trace a word", it, style = MaterialTheme.typography.bodySmall) },
                    )
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it },
                        singleLine = true,
                        label = { Text("Word for this path…") },
                        modifier = Modifier.fillMaxWidth().testTag(FIELD),
                    )
                }
            },
            actionArea = { areaModifier ->
                ActionArea(passed = null, canTestSolve = false, onTestSolve = {}, blocks = emptyList(), modifier = areaModifier)
            },
        )
    }

    /** A square grid of [ROWS] tagged rows, sized like a game's: the slot's smaller side. */
    @Composable
    private fun Board(modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true).testTag(GRID)) {
                repeat(ROWS) { index ->
                    val colour = if (index % 2 == 0) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh
                    Box(
                        Modifier.weight(1f).fillMaxWidth().background(colour).testTag(row(index)),
                        contentAlignment = Alignment.Center,
                    ) { Text("row ${index + 1}", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }

    private companion object {
        const val ROWS = 8
        const val GRID = "ime.board.grid"
        const val FIELD = "ime.word"
        const val ADJUST = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        val CHROME = 80.dp

        fun row(index: Int) = "ime.board.row.$index"
    }
}
