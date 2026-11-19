package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow

/** The large bar's sub line on one line, cut with an ellipsis; with [onClick] it is a button that [onClickLabel] names. */
@Composable
fun LargeTopBarSubtitle(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Text(
        text = text,
        modifier = if (onClick != null) modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
