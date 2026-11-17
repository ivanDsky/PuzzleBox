package com.ivandsky.puzzlebox.play.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.CommunityRoute
import com.ivandsky.puzzlebox.play.CustomSlot
import com.ivandsky.puzzlebox.play.DuelsSlot
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey

/**
 * The Community tab's route (nav-rethink M10-1): [CommunityKey] draws the app's [custom] slot, with the friend puzzles,
 * under a Community head. A friend's puzzle pushes its [FriendPuzzleKey] card on the Community stack, so back returns
 * here. The code sheet opens on [codeDraft]'s text and hands it back through [onCodeDraftTaken]; [duels] is what it
 * calls when a code names a duel. The key is registered with no slot too (the tab is not shown then) and draws nothing.
 */
fun EntryProviderScope<NavKey>.communityEntry(
    navigator: Navigator,
    layout: PlayLayout,
    custom: CustomSlot? = null,
    codeDraft: () -> String? = { null },
    onCodeDraftTaken: () -> Unit = {},
    duels: DuelsSlot? = null,
) {
    entry<CommunityKey> {
        CommunityRoute(
            layout = layout,
            custom = custom,
            onOpen = { code -> navigator.pushOnce(FriendPuzzleKey(code.value)) },
            codeDraft = codeDraft(),
            onCodeDraftTaken = onCodeDraftTaken,
            onOpenDuel = duels?.onOpenDuel,
        )
    }
}
