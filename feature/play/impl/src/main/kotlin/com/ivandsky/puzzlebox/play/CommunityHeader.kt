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
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.LargeTopBarSubtitle
import com.ivandsky.puzzlebox.designsystem.LargeTopBarTitle
import com.ivandsky.puzzlebox.designsystem.SecondaryButton

/**
 * The Community tab's head: its name and one line of what it holds, with Enter code at the end.
 */
@Composable
internal fun CommunityHeader(onEnterCode: () -> Unit, modifier: Modifier = Modifier) {
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
    }
}

/**
 * The same head on Today's large top bar (MainTabsLargeBar): Enter code, then the phone's avatar at the end. On the
 * tablet ([phone] false) the bar reaches into the panes' gutters ([mainTabBarBleed]).
 */
@Composable
internal fun CommunityLargeHeader(onEnterCode: () -> Unit, phone: Boolean) {
    LargeTopBar(
        title = { LargeTopBarTitle("Community") },
        modifier = Modifier.mainTabBarBleed(phone),
        subtitle = { LargeTopBarSubtitle("Puzzles from you and your friends") },
        actions = { SecondaryButton(text = "Enter code", onClick = onEnterCode, modifier = Modifier.testTag("community.enterCode")) },
    )
}
