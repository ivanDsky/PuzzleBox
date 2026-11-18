package com.ivandsky.puzzlebox.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.compete.create.DuelChallenge
import com.ivandsky.puzzlebox.compete.tab.DuelsTab
import com.ivandsky.puzzlebox.compete.tab.duelsPane
import com.ivandsky.puzzlebox.compete.tab.rememberDuelsNeedYou
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.DuelsSlot

/**
 * The Games screen's Duels tab from the compete module. On the two-pane layout a row selects its duel for the right
 * [DuelsPane] rather than opening the duel screen; on the phone a row opens the screen, and [DuelsSlot.pane] is never drawn.
 */
@Composable
internal fun rememberDuelsSlot(navigator: Navigator, layout: PlayLayout): DuelsSlot = remember(navigator, layout) {
    DuelsSlot(
        content = { listState, prefill, onTaken ->
            DuelsTab(
                onOpenDuel = navigator::pushOnce,
                onOpenReveal = navigator::pushOnce,
                onNewDuel = navigator::pushOnce,
                listState = listState,
                challenge = prefill?.let { DuelChallenge(it.friendUid, it.friendName, it.game) },
                onChallengeTaken = onTaken,
                inPane = layout == PlayLayout.TwoPane,
            )
        },
        needYou = { rememberDuelsNeedYou() },
        onOpenDuel = navigator::pushOnce,
        pane = duelsPane(navigator, layout),
    )
}
