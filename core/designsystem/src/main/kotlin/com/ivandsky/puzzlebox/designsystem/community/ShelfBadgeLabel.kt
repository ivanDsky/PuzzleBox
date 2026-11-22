package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The status label on a shelf card: NEW on primary, Friend on the secondary container, You on the tertiary one. */
@Composable
internal fun ShelfBadgeLabel(badge: ShelfBadge, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (fill, ink) = when (badge) {
        ShelfBadge.New -> scheme.primary to scheme.onPrimary
        ShelfBadge.Friend -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ShelfBadge.You -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Text(
        text = badge.label,
        modifier = modifier.background(fill, ShelfCardDefaults.BadgeShape).padding(horizontal = Spacing.sm, vertical = 2.dp),
        color = ink,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.33.sp),
    )
}
