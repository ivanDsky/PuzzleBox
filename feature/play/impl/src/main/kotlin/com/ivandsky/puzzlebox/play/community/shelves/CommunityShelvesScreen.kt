package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.LargeTopBarSubtitle
import com.ivandsky.puzzlebox.designsystem.LargeTopBarTitle
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.play.mainTabBarBleed

/**
 * The Community tab's shelves home (epic community-browse), behind [com.ivandsky.puzzlebox.play.flags.PlayFlag.CommunityShelves]:
 * for now a stub of the head only, Community with one line of what it holds and Enter code ([onEnterCode]). On the tablet
 * ([phone] false) the bar reaches into the panes' gutters ([mainTabBarBleed]).
 */
@Composable
internal fun CommunityShelvesScreen(onEnterCode: () -> Unit, phone: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.testTag("community.shelves")) {
        LargeTopBar(
            title = { LargeTopBarTitle("Community") },
            modifier = Modifier.mainTabBarBleed(phone),
            subtitle = { LargeTopBarSubtitle("Shelves of puzzles to browse") },
            actions = { SecondaryButton(text = "Enter code", onClick = onEnterCode, modifier = Modifier.testTag("community.enterCode")) },
        )
    }
}
