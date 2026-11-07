package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * A [GameButton] that holds a mode (Sudoku Notes): neutral while off, filled with
 * [accent] while on, announced as a switch with its On/Off state.
 */
@Composable
fun GameToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    label: String,
    accent: AccentSlot,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: GameButtonSize = GameButtonSize.Regular,
) {
    val interactionSource = remember { MutableInteractionSource() }
    GameButtonFace(
        colors = gameButtonColors(if (checked) accent.colors() else null, MaterialTheme.colorScheme, LocalNeutralLip.current),
        enabled = enabled,
        interactionSource = interactionSource,
        label = label,
        describe = !size.showsLabel,
        size = size,
        modifier = modifier
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) { ink -> GameButtonContent(icon, label.takeIf { size.showsLabel }, ink) }
}

// Previews — light + dark in PuzzleBoxTheme: off, on (accent fill), disabled.
@ShowkaseComposable(name = "GameToggleButton", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun GameToggleButtonPreview() {
    PuzzleBoxTheme {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            GameToggleButton(checked = false, onCheckedChange = {}, icon = PuzzleIcons.Notes, label = "Notes", accent = AccentSlot.Teal)
            GameToggleButton(checked = true, onCheckedChange = {}, icon = PuzzleIcons.NotesOn, label = "Notes", accent = AccentSlot.Teal)
            GameToggleButton(checked = false, onCheckedChange = {}, icon = PuzzleIcons.Notes, label = "Notes", accent = AccentSlot.Teal, enabled = false)
        }
    }
}
