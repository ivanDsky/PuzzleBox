package com.ivandsky.puzzlebox.profile.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

private val RowMinHeight = 72.dp

/**
 * One row of the Accounts card: a [lead] mark, a body of [title] and wrapping [sub], an optional [trailing] action,
 * and an optional full-width [extra] line below them (a button or the stats text). The title keeps one line so a
 * long name cannot push the trailing action out; the sub wraps.
 */
@Composable
internal fun AccountRow(
    lead: @Composable () -> Unit,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    subColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    subWeight: FontWeight? = null,
    trailing: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            lead()
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = subColor,
                    fontWeight = subWeight,
                )
            }
            trailing?.invoke()
        }
        extra?.invoke()
    }
}
