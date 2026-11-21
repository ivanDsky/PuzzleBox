package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.LargeTopBarSubtitle
import com.ivandsky.puzzlebox.designsystem.LargeTopBarTitle
import com.ivandsky.puzzlebox.designsystem.SecondaryButton

/**
 * The Community tab's head on Today's large top bar: its name and one line of what it holds, Enter code, then the
 * phone's avatar at the end. On the tablet ([phone] false) the bar reaches into the panes' gutters ([mainTabBarBleed]).
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
