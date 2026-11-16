package com.ivandsky.puzzlebox.app.debug.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.DeviceClass
import com.ivandsky.puzzlebox.gameui.LandscapeControls
import org.koin.compose.koinInject

private const val DEFAULT_LANDSCAPE_CAP_DP = 552

/** The stepper buttons keep the 48 dp touch target; IconButton alone is 40 dp. */
private val STEPPER_BUTTON = 48.dp

/**
 * Settings' Play tuning section (nav-rethink B6): a board cap stepper per device class, Fill the pane for the tablet
 * landscape cap, a tablet cell cap stepper with an off state, the landscape controls switch and Reset (back to [com.ivandsky.puzzlebox.gameui.PlayTuning.Shipped]). Every change
 * goes through [source] and reaches an open play screen live. The values apply only while BoardErgonomics is on. Debug only: release compiles none of this.
 */
@Composable
internal fun PlayTuningPanel(
    modifier: Modifier = Modifier,
    source: DebugPlayTuningSource = koinInject(),
) {
    val tuning by source.tuning.collectAsStateWithLifecycle()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            "These values apply only while the BoardErgonomics release flag (Fixed play controls and board size caps) is on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CapStepper("Phone board cap", "phone", tuning.phoneCap) { source.setCap(DeviceClass.Phone, it) }
        CapStepper("Tablet portrait board cap", "tabletPortrait", tuning.tabletPortraitCap) {
            source.setCap(DeviceClass.TabletPortrait, it)
        }
        val landscapeCap = tuning.tabletLandscapeCap
        if (landscapeCap == null) {
            Text(
                "Tablet landscape board cap: fills the pane",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            CapStepper("Tablet landscape board cap", "tabletLandscape", landscapeCap) {
                source.setCap(DeviceClass.TabletLandscape, it)
            }
        }
        SwitchRow("Fill the pane (tablet landscape)", "tuning.fill", landscapeCap == null) { fill ->
            if (fill) source.fillTabletLandscapePane() else source.setCap(DeviceClass.TabletLandscape, DEFAULT_LANDSCAPE_CAP_DP.dp)
        }
        val cellCap = tuning.tabletCellCap
        if (cellCap == null) {
            Text(
                "Tablet cell cap: off",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            CapStepper(
                "Tablet cell cap",
                "cell",
                cellCap,
                min = DebugPlayTuningSource.MIN_CELL_CAP_DP,
                max = DebugPlayTuningSource.MAX_CELL_CAP_DP,
            ) { source.setTabletCellCap(it) }
        }
        SwitchRow("Tablet cell cap on", "tuning.cellCap", cellCap != null) { on ->
            source.setTabletCellCap(if (on) DebugPlayTuningSource.DEFAULT_CELL_CAP_DP.dp else null)
        }
        SwitchRow(
            "Landscape controls in a side column",
            "tuning.sideColumn",
            tuning.landscapeControls == LandscapeControls.SideColumn,
        ) { side ->
            source.setLandscapeControls(if (side) LandscapeControls.SideColumn else LandscapeControls.UnderBoard)
        }
        TextButton(onClick = source::reset, modifier = Modifier.testTag("tuning.reset")) { Text("Reset") }
    }
}

@Composable
private fun CapStepper(
    label: String,
    id: String,
    cap: Dp,
    min: Int = DebugPlayTuningSource.MIN_CAP_DP,
    max: Int = DebugPlayTuningSource.MAX_CAP_DP,
    onCap: (Dp) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("tuning.$id"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onCap(cap - DebugPlayTuningSource.STEP_DP.dp) },
            enabled = cap.value > min,
            modifier = Modifier.size(STEPPER_BUTTON).semantics { contentDescription = "Smaller $label" }.testTag("tuning.$id.less"),
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            "${cap.value.toInt()} dp",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("tuning.$id.value"),
        )
        IconButton(
            onClick = { onCap(cap + DebugPlayTuningSource.STEP_DP.dp) },
            enabled = cap.value < max,
            modifier = Modifier.size(STEPPER_BUTTON).semantics { contentDescription = "Larger $label" }.testTag("tuning.$id.more"),
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun SwitchRow(label: String, tag: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked, modifier = Modifier.testTag(tag))
    }
}
