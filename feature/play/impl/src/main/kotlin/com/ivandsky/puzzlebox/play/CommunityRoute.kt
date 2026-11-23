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
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.data.repository.SharedTextStore
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.friend.EnterCodeSheet
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * The Community tab's root: the shelves home ([CommunityShelvesRoute]) with the friend puzzles and Custom's list behind
 * it. A [codeDraft] opens the code sheet with that text in the field and is handed back through [onCodeDraftTaken];
 * the sheet's code opens through [onOpen], and a code that names a duel through [onOpenDuel] (null: duels are off, the
 * sheet never tries a code as one).
 *
 * On the phone a card opens a page of its own through [onOpen]. [PlayLayout.TwoPane] keeps the home in a list pane
 * and opens the card's page on the right, with Play opening the board full screen through [onPlay].
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
    val removedFriend by viewModel.removedFriend.collectAsStateWithLifecycle()
    val removedFriends by viewModel.removedFriends.collectAsStateWithLifecycle()
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
    WithNewPuzzle(custom) { create ->
        val clearance = if (create != null) CreateFabClearance else 0.dp
        Box(modifier.fillMaxSize().testTag("route.community")) {
            CommunityShelvesRoute(
                layout, custom, onOpen, onPlay, onEnterCode, create, clearance,
                onRemoveFriend = viewModel::removeFriend,
                sharePending = sharePending != null,
                removedFriends = removedFriends,
            )
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
