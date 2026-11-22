package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.data.repository.SharedTextStore
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.community.CommunityBound
import com.ivandsky.puzzlebox.play.community.CommunityViewModel
import com.ivandsky.puzzlebox.play.flags.PlayFlag
import com.ivandsky.puzzlebox.play.friend.EnterCodeSheet
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * The Community tab's root (nav-rethink M10-1): Custom's list and the friend puzzles, as a tab of their own. A
 * [codeDraft] opens the code sheet with that text in the field and is handed back through [onCodeDraftTaken]; the
 * sheet's code opens through [onOpen], and a code that names a duel through [onOpenDuel] (null: duels are off, the
 * sheet never tries a code as one).
 *
 * On the phone it is three tabs under the head ([CommunityTabs]: New today, Friends, Yours) and a row opens a page of
 * its own through [onOpen]. [PlayLayout.TwoPane] is Games' two-pane ([CommunityPanes]): the same three tabs at Games'
 * list width, and on the right the page of the current tab's pick, the page that tap opens on the phone, with Play
 * opening the board full screen through [onPlay]. Each tab keeps its own pick; until one is made the tab's first
 * puzzle shows (a draft never does: it opens the Studio).
 */
@Composable
internal fun CommunityRoute(
    layout: PlayLayout,
    custom: CustomSlot,
    onOpen: (ShareCode) -> Unit,
    onPlay: (CustomGameKey) -> Unit,
    modifier: Modifier = Modifier,
    codeDraft: String? = null,
    onCodeDraftTaken: () -> Unit = {},
    onOpenDuel: ((DuelKey) -> Unit)? = null,
) {
    val viewModel: LibraryViewModel = koinViewModel(parameters = { parametersOf(false) })
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val removedFriend by viewModel.removedFriend.collectAsStateWithLifecycle()
    val community: CommunityViewModel = koinViewModel()
    val communityState by community.state.collectAsStateWithLifecycle()
    val sharePending by koinInject<SharedTextStore>().pending.collectAsStateWithLifecycle()
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
    val onEnterCode = { enteringCode = true }
    val flags by koinInject<ReleaseFlags>().enabled.collectAsStateWithLifecycle()
    WithNewPuzzle(custom, enabled = PlayFlag.CreateFab in flags) { onNewPuzzle ->
        val clearance = if (onNewPuzzle != null) CreateFabClearance else 0.dp
        Box(modifier.fillMaxSize().testTag("route.community")) {
            if (PlayFlag.CommunityShelves in flags) {
                CommunityShelvesRoute(layout, custom, onOpen, onPlay, onEnterCode, onNewPuzzle, clearance)
            } else if (layout == PlayLayout.SinglePane) {
                CommunityBound(community, communityState, onOpen) { actions ->
                    CommunityTabs(
                        communityState, actions, friends, custom, onOpen, viewModel::removeFriend, onEnterCode, Modifier.fillMaxSize(),
                        sharePending = sharePending != null,
                        onNewPuzzle = onNewPuzzle,
                    )
                }
            } else {
                val yours = custom.yoursSummary()
                CommunityBound(community, communityState, onOpen) { actions ->
                    CommunityPanes(
                        default = { tab -> defaultCommunityPick(tab, communityState.phase, friends, yours) },
                        list = { size, picks ->
                            CommunityTabs(
                                communityState, actions, friends, custom, onOpen, viewModel::removeFriend, onEnterCode, size, picks,
                                sharePending = sharePending != null,
                                onNewPuzzle = onNewPuzzle,
                            )
                        },
                        empty = { tab -> CommunityEmptyPane(tab, communityState.phase) },
                        detail = { pick, clear -> CommunityPage(pick, clear, custom, onPlay) },
                        listOverlay = onNewPuzzle?.let { open -> { CommunityCreateFab(open, Modifier.align(Alignment.BottomEnd)) } },
                    )
                }
            }
            // The FAB floats over the whole screen where the list pane is not its own: the phone, and the shelves home.
            if (onNewPuzzle != null && (layout == PlayLayout.SinglePane || PlayFlag.CommunityShelves in flags)) {
                CommunityCreateFab(onNewPuzzle, Modifier.align(Alignment.BottomEnd))
            }
            FriendRemovedSnackbar(
                removedFriend,
                onUndo = viewModel::undoRemoveFriend,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = clearance),
            )
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
}

/** The right pane's page for [pick]: the friend puzzle page, or the app's published page for one of your own. */
@Composable
internal fun CommunityPage(pick: CommunityPick, clear: () -> Unit, custom: CustomSlot, onPlay: (CustomGameKey) -> Unit) {
    when (pick) {
        is CommunityPick.Puzzle -> FriendPuzzleRoute(FriendPuzzleKey(pick.code), onPlay = onPlay, onBack = clear, showUp = false)
        is CommunityPick.Published -> custom.published?.invoke(pick.code, Modifier.fillMaxSize())
    }
}
