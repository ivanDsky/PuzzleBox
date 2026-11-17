package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The Community tab's list under its head: "Community" with Enter code, then the [custom] slot's rows with the From
 * friends rows among them ([CustomTabBody]). On the [phone] it fills the screen and its rows pad their own sides; on the
 * tablet it is the list pane [modifier] gives it, and its rows step out of the pane's inset as Games' do.
 * A friend's puzzle opens through [onOpen], or through [LocalCommunityPicker] when the tablet's panes are around it.
 */
@Composable
internal fun CommunityList(
    custom: CustomSlot,
    listState: LazyListState,
    friends: List<FriendCardModel>,
    phone: Boolean,
    onOpen: (ShareCode) -> Unit,
    onRemove: (ShareCode) -> Unit,
    onEnterCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = LocalCommunityPicker.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(if (phone) Modifier.padding(horizontal = Spacing.lg) else Modifier) { CommunityHeader(onEnterCode) }
        Box(Modifier.weight(1f).fillMaxWidth().then(if (phone) Modifier else Modifier.widenBy(Spacing.lg))) {
            CustomTabBody(
                slot = custom,
                listState = listState,
                friends = friends,
                onOpen = { code -> picker?.puzzle(code.value) ?: onOpen(code) },
                onRemove = onRemove,
                onEnterCode = onEnterCode,
            )
        }
    }
}

/** The tab's head: its name and one line of what it holds, with Enter code at the end. */
@Composable
private fun CommunityHeader(onEnterCode: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
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
