package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.SecondaryButton

/**
 * The Community tab's head: its name and one line of what it holds, with Enter code at the end and, after it,
 * [trailing] (the phone's filter ⋮ slot).
 */
@Composable
internal fun CommunityHeader(onEnterCode: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Community", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Puzzles from you and your friends",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SecondaryButton(text = "Enter code", onClick = onEnterCode, modifier = Modifier.testTag("community.enterCode"))
        trailing?.invoke()
    }
}
