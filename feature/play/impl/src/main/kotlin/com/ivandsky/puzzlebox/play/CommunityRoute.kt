package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.friend.EnterCodeSheet
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Community tab's root (nav-rethink M10-1): what Games › Custom showed, as a tab of its own. Under a "Community"
 * head with Enter code it lays out the app's [custom] slot (New today, your puzzles, Make a puzzle) with the From
 * friends rows among them ([CustomTabBody]). A friend's puzzle opens through [onOpen]. A [codeDraft] opens the code
 * sheet with that text in the field and is handed back through [onCodeDraftTaken]; the sheet's code opens through
 * [onOpen], and a code that names a duel through [onOpenDuel] (null: duels are off, the sheet never tries a code as
 * one). A reselect of the tab scrolls the list to its top. Without a [custom] slot there is nothing to show.
 * [PlayLayout.TwoPane] keeps the list at Games' list width, beside nothing: a puzzle opens as a page of its own.
 */
@Composable
internal fun CommunityRoute(
    layout: PlayLayout,
    custom: CustomSlot?,
    onOpen: (ShareCode) -> Unit,
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
    Box(modifier.fillMaxSize().testTag("route.community")) {
        // The tablet's list is Games' list pane: same insets and width; its rows step out of the inset as Games' do.
        val pane = if (phone) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(start = Spacing.xl, top = Spacing.lg, end = Spacing.xxl, bottom = Spacing.xl)
        Row(pane) {
            Column(if (phone) Modifier.fillMaxSize() else Modifier.width(400.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(if (phone) Modifier.padding(horizontal = Spacing.lg) else Modifier) { CommunityHeader(onEnterCode = { enteringCode = true }) }
                Box(Modifier.weight(1f).fillMaxWidth().then(if (phone) Modifier else Modifier.widenBy(Spacing.lg))) {
                    CustomTabBody(custom, listState, friends, onOpen, viewModel::removeFriend, onEnterCode = { enteringCode = true })
                }
            }
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
