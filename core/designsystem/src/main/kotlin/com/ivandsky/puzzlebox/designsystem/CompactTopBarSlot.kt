package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The detail-screen bar around a [title] the screen draws itself, for a title of more than one part. The title sits
 * between the navigation icon and the actions; give it `Modifier.weight(1f)` so the actions keep to the bar's end.
 * The one-title `CompactTopBar` in `TopBars.kt` is this bar with a single text, so its preview draws this one too.
 */
@Composable
fun CompactTopBar(
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (navigationIcon != null) {
            navigationIcon()
        }
        title()
        actions()
        LocalShellActions.current.invoke(this)
    }
}
