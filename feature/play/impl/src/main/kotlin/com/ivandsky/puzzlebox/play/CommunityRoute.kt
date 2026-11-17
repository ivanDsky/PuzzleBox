package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.community.CommunityViewModel
import com.ivandsky.puzzlebox.play.friend.EnterCodeSheet
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Community tab's root (nav-rethink M10-1): what Games › Custom showed, as a tab of its own. Under a "Community"
 * head with Enter code it lays out the app's [custom] slot (New today, your puzzles, Make a puzzle) with the From
 * friends rows among them ([CommunityList]). A [codeDraft] opens the code sheet with that text in the field and is
 * handed back through [onCodeDraftTaken]; the sheet's code opens through [onOpen], and a code that names a duel
 * through [onOpenDuel] (null: duels are off, the sheet never tries a code as one). A reselect of the tab scrolls the
 * list to its top. Without a [custom] slot there is nothing to show.
 *
 * On the phone a row opens a page of its own through [onOpen]. [PlayLayout.TwoPane] is Games' two-pane
 * ([CommunityPanes]): the list at Games' list width, and a row's puzzle on the right as the page that tap opens on the
 * phone, with Play opening the board full screen through [onPlay]. The first New today puzzle shows until one is picked.
 */
@Composable
internal fun CommunityRoute(
    layout: PlayLayout,
    custom: CustomSlot?,
    onOpen: (ShareCode) -> Unit,
    onPlay: (CustomGameKey) -> Unit,
    modifier: Modifier = Modifier,
    codeDraft: String? = null,
    onCodeDraftTaken: () -> Unit = {},
    onOpenDuel: ((DuelKey) -> Unit)? = null,
) {
    if (custom == null) return
    val viewModel: LibraryViewModel = koinViewModel(parameters = { parametersOf(false) })
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val removedFriend by viewModel.removedFriend.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var enteringCode by rememberSaveable { mutableStateOf(false) }
    var codePrefill by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(codeDraft) {
        if (codeDraft != null) {
            codePrefill = codeDraft
            enteringCode = true
            onCodeDraftTaken()
        }
    }
    // A closed sheet forgets the draft: the next Enter code opens empty.
    if (!enteringCode && codePrefill.isNotEmpty()) LaunchedEffect(Unit) { codePrefill = "" }
    OnTabReselect { listState.animateScrollToItem(0) }
    val phone = layout == PlayLayout.SinglePane
    val list: @Composable (Modifier) -> Unit = { size ->
        CommunityList(
            custom = custom,
            listState = listState,
            friends = friends,
            phone = phone,
            onOpen = onOpen,
            onRemove = viewModel::removeFriend,
            onEnterCode = { enteringCode = true },
            modifier = size,
        )
    }
    Box(modifier.fillMaxSize().testTag("route.community")) {
        if (phone) {
            list(Modifier.fillMaxSize())
        } else {
            val phase = koinViewModel<CommunityViewModel>().state.collectAsStateWithLifecycle().value.phase
            CommunityPanes(
                default = defaultCommunityPick(phase, friends),
                list = list,
                empty = { CommunityEmptyPane(phase) },
                detail = { pick, clear -> CommunityPage(pick, clear, custom, onPlay) },
            )
        }
        FriendRemovedSnackbar(removedFriend, onUndo = viewModel::undoRemoveFriend, modifier = Modifier.align(Alignment.BottomCenter))
        if (enteringCode) {
            EnterCodeSheet(
                onOpen = { code ->
                    enteringCode = false
                    onOpen(code)
                },
                onDismiss = { enteringCode = false },
                initialText = codePrefill,
                onOpenDuel = onOpenDuel?.let { openDuel ->
                    { key ->
                        enteringCode = false
                        openDuel(key)
                    }
                },
            )
        }
    }
}

/** The right pane's page for [pick]: the friend puzzle page, or the app's published page for one of your own. */
@Composable
private fun CommunityPage(pick: CommunityPick, clear: () -> Unit, custom: CustomSlot, onPlay: (CustomGameKey) -> Unit) {
    when (pick) {
        is CommunityPick.Puzzle -> FriendPuzzleRoute(FriendPuzzleKey(pick.code), onPlay = onPlay, onBack = clear, showUp = false)
        is CommunityPick.Published -> custom.published?.invoke(pick.code, Modifier.fillMaxSize())
    }
}
