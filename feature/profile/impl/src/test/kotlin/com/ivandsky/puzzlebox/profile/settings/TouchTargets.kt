package com.ivandsky.puzzlebox.profile.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/**
 * Every clickable node on screen reserves at least 48 dp on both axes: the size of its whole layout node, which is
 * where a Material control's minimum-interactive-size padding lands (a bare 40 dp icon button counts as 48, a bare
 * 20 dp `clickable` does not). The framework's own 48 dp touch slop is not counted, it would pass anything. Names the
 * offenders by test tag or text, so a failure says which control is too small.
 */
internal fun ComposeTestRule.assertTouchTargetsAtLeast48() {
    val minPx = with(density) { 48.dp.toPx() } - 1f
    val nodes = onAllNodes(hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes()
    assertTrue("no clickable node on screen", nodes.isNotEmpty())
    val small = nodes.filter { it.layoutInfo.width < minPx || it.layoutInfo.height < minPx }.map { node ->
        val name = node.config.getOrNull(SemanticsProperties.TestTag)
            ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: "node ${node.id}"
        "$name ${with(density) { node.layoutInfo.width.toDp() }} x ${with(density) { node.layoutInfo.height.toDp() }}"
    }
    assertTrue("touch targets under 48 dp: $small", small.isEmpty())
}

/** Plays a device set to the 24-hour clock; Robolectric resets the setting with each test. */
internal fun use24HourClock() {
    val resolver = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
    android.provider.Settings.System.putString(resolver, android.provider.Settings.System.TIME_12_24, "24")
}
