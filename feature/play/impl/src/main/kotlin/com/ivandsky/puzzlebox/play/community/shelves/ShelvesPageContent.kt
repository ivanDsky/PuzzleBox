package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.CustomSlot
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllPage
import com.ivandsky.puzzlebox.play.community.shelves.yours.YourPuzzlesPage

/** The screen of [page]: a See all page of its scope, or Your puzzles over [custom]'s list. A row opens through [onOpen]. [visit] counts the opens of the page (a new one starts it clean); [bottomPadding] is the room kept under the list; [removedFriends] are the friends' puzzles in their Undo window, which From friends leaves out. */
@Composable
internal fun ShelvesPageContent(
    page: ShelvesPage,
    custom: CustomSlot,
    onBack: () -> Unit,
    onOpen: (ShareCode) -> Unit,
    modifier: Modifier = Modifier,
    visit: Int = 0,
    bottomPadding: Dp = 0.dp,
    removedFriends: Set<ShareCode> = emptySet(),
) {
    when (page) {
        is ShelvesPage.SeeAll -> SeeAllPage(page.scope, onBack, onOpen, modifier, visit, bottomPadding, removedFriends)
        ShelvesPage.Yours -> YourPuzzlesPage(custom.yours, onBack, modifier, bottomPadding)
    }
}
