package com.ivandsky.puzzlebox.compete.create.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.compete.create.FriendChoices
import com.ivandsky.puzzlebox.compete.create.NewDuelActions
import com.ivandsky.puzzlebox.compete.create.NewDuelIntro
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.Opponent
import com.ivandsky.puzzlebox.compete.create.PlayAgainstRow
import com.ivandsky.puzzlebox.compete.create.SelfChoices
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The intro line, the "Play against" row when offered, then the friend's choices or Past you's; it scrolls on its own. */
@Composable
internal fun NewDuelForm(state: NewDuelState, actions: NewDuelActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = NewDuelMaxWidth)
                .fillMaxWidth()
                .testTag("duel.new.form")
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            NewDuelIntro(state.opponent)
            if (state.selfOffered) PlayAgainstRow(state.opponent, state.selfName, actions.onOpponent)
            when (state.opponent) {
                Opponent.Friend -> FriendChoices(state, actions)
                Opponent.PastYou -> SelfChoices(state, actions)
            }
        }
    }
}
