package com.ivandsky.puzzlebox.play.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.CommunityRoute
import com.ivandsky.puzzlebox.play.CustomSlot
import com.ivandsky.puzzlebox.play.DuelsSlot
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey

/**
 * The Community tab's route (nav-rethink M10-1): [CommunityKey] draws the app's [custom] slot, with the friend puzzles,
 * under a Community head. On the phone a friend's puzzle pushes its [FriendPuzzleKey] card on the Community stack, so
 * back returns here; on the tablet it shows beside the list and nothing is pushed, and its Play pushes the board
 * ([CustomGameKey]) over the two-pane, so back returns to the same selection. The code sheet opens on [codeDraft]'s
 * text and hands it back through [onCodeDraftTaken]; [duels] is what it calls when a code names a duel.
 */
fun EntryProviderScope<NavKey>.communityEntry(
    navigator: Navigator,
    layout: PlayLayout,
    custom: CustomSlot,
    codeDraft: () -> String? = { null },
    onCodeDraftTaken: () -> Unit = {},
    duels: DuelsSlot? = null,
) {
    entry<CommunityKey> {
        CommunityRoute(
            layout = layout,
            custom = custom,
            onOpen = { code -> navigator.pushOnce(FriendPuzzleKey(code.value)) },
            onPlay = { game -> navigator.pushOnce(game) },
            codeDraft = codeDraft(),
            onCodeDraftTaken = onCodeDraftTaken,
            onOpenDuel = duels?.onOpenDuel,
        )
    }
}
