package com.ivandsky.puzzlebox.compete.create.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.compete.create.FriendCreateFooter
import com.ivandsky.puzzlebox.compete.create.NewDuelActions
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.Opponent
import com.ivandsky.puzzlebox.compete.create.SelfCreateFooter
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The bar pinned under the form: the notice, the summary line and Create duel, the footer of whoever the duel is against. */
@Composable
internal fun NewDuelCreateBar(state: NewDuelState, actions: NewDuelActions, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = Spacing.xs) {
        Box(contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = NewDuelMaxWidth).fillMaxWidth().testTag("duel.new.createBar").padding(Spacing.lg)) {
                when (state.opponent) {
                    Opponent.Friend -> FriendCreateFooter(state, actions)
                    Opponent.PastYou -> SelfCreateFooter(state, actions)
                }
            }
        }
    }
}
