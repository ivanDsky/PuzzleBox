package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * The Studio's one keyboard policy (device check R3), the same for every game. The window draws
 * edge to edge and the manifest adjusts it by resize, never by pan (a pan slid the top bar under
 * the status bar), so the keyboard resizes nothing and the Studio reads [WindowInsets.ime]
 * itself. [measure], on the Studio's root, finds the window's bottom chrome under the Studio
 * (the tab bar, the navigation bar), which the keyboard covers first; [padding] pads by the
 * rest, the part of the keyboard over the Studio, and consumes the IME inset for what it holds.
 * Both read the inset at layout, so the keyboard's slide relays the panes out without
 * recomposing them.
 */
@Stable
internal class StudioKeyboard(private val ime: WindowInsets) {
    /** Px from the Studio's bottom edge to the window's. */
    private var chrome by mutableIntStateOf(0)

    private val overStudio: WindowInsets = ime.exclude(BottomChrome())

    val measure: Modifier = Modifier.onGloballyPositioned { coordinates ->
        val root = coordinates.findRootCoordinates()
        chrome = root.size.height - (coordinates.positionInRoot().y.roundToInt() + coordinates.size.height)
    }

    val padding: Modifier = Modifier.windowInsetsPadding(overStudio).consumeWindowInsets(ime)

    /** Whether a keyboard shows at all, even one still behind the chrome as it slides in. */
    fun shows(density: Density): Boolean = ime.getBottom(density) > 0

    private inner class BottomChrome : WindowInsets {
        override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
        override fun getTop(density: Density) = 0
        override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
        override fun getBottom(density: Density) = chrome
    }
}

/** The Studio's [StudioKeyboard]. */
@Composable
internal fun rememberStudioKeyboard(): StudioKeyboard {
    val ime = WindowInsets.ime
    return remember(ime) { StudioKeyboard(ime) }
}

/**
 * [content] at the height it has with the keyboard closed (the board keeps its size; it never
 * shrinks to fit) in a viewport that ends at the [keyboard]'s top. While the keyboard shows the
 * part it covers scrolls into reach, and a focused field in [content] stays in view: the scroll
 * attaches and lays out once at full size before the keyboard's padding lands, so the viewport
 * shrinks under it even when the keyboard arrives in one step (API 26-29, animations off).
 * Closed, there is no scroll at all, nor its clip: the layout is the keyboard-less one.
 */
@Composable
internal fun KeyboardScroll(keyboard: StudioKeyboard, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    val density = LocalDensity.current
    val open by remember(keyboard, density) { derivedStateOf { keyboard.shows(density) } }
    BoxWithConstraints(modifier) {
        val height = constraints.maxHeight
        // Whether the scroll has laid out at full size since the keyboard opened.
        var placed by remember(open) { mutableStateOf(false) }
        val scroll = if (open) Modifier.verticalScroll(remember { ScrollState(0) }).onPlaced { placed = true } else Modifier
        Box(Modifier.fillMaxSize().then(if (placed) keyboard.padding else Modifier).testTag(KeyboardScrollTag).then(scroll)) {
            content(
                Modifier.layout { measurable, outer ->
                    val placeable = measurable.measure(outer.copy(minHeight = height, maxHeight = height))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
            )
        }
    }
}

/** The viewport over the keyboard's top: the phone's column, the tablet's board pane. */
private const val KeyboardScrollTag = "create.studio.keyboardScroll"
