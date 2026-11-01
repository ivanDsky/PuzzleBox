package com.ivandsky.puzzlebox.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

// Compact radio metrics: a 16dp dot with a 2dp ring (selected adds a filled core) —
// the stock M3 RadioButton enforces a 48dp interactive footprint, which would push the
// seven options far past one row even before the labels. 16dp + 2dp gaps + labelSmall
// is what keeps the row near one-line width on a narrow phone; the 10dp vertical
// padding gives each option a ~36dp tap target.
private val RadioDotSize = 16.dp
private val RadioRingWidth = 2.dp
private val RadioCoreSize = 6.dp

/**
 * Ticket showkase-v2-09: the theme (System/Light/Dark) and backdrop (Checker/Surface/
 * White/Black) controls as ONE inline row of compact radio groups — direct selection
 * replacing the ticket-06/07 cycle chips. There is deliberately NO group-name text:
 * two extra labels cost ~45dp and push the row past 440dp (the seven radio labels
 * alone total ~230dp) — exactly the multi-line/scroll trap the brief forbids. The
 * divider separates the groups and the option words are self-descriptive, so the row
 * measures ≈360dp and fits a narrow phone without scrolling; the horizontal scroll is
 * kept only as the font-scale escape hatch (at 1.5x the row overflows and scrolls).
 *
 * The home screen hosts it directly under the search bar (user addendum); the detail
 * screen reuses it verbatim as its own toolbar row so variant panes are themeable on
 * the spot. State stays with the browser (session-scoped, rememberSaveable) — this
 * composable is a pure view: values in, selection events out.
 */
@Composable
internal fun BrowserRadioControlRow(
    themeOverride: BrowserThemeOverride,
    onThemeOverrideChange: (BrowserThemeOverride) -> Unit,
    backdropStyle: PreviewBackdropStyle,
    onBackdropStyleChange: (PreviewBackdropStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        BrowserThemeOverride.entries.forEach { position ->
            RadioOption(
                label = position.label,
                selected = position == themeOverride,
                onSelect = { onThemeOverrideChange(position) },
            )
        }
        VerticalDivider(Modifier.height(RadioDotSize + 4.dp))
        PreviewBackdropStyle.entries.forEach { style ->
            RadioOption(
                label = style.label,
                selected = style == backdropStyle,
                onSelect = { onBackdropStyleChange(style) },
            )
        }
    }
}

/**
 * One selectable option: radio dot + label in a single merged selectable node
 * (Role.RadioButton), so a11y reads "Dark, radio button, selected" and the whole
 * option is the tap target — no per-control 48dp minimums.
 */
@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RadioDot(selected)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val ring = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .size(RadioDotSize)
            .border(RadioRingWidth, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(RadioCoreSize)
                    .background(ring, CircleShape),
            )
        }
    }
}
