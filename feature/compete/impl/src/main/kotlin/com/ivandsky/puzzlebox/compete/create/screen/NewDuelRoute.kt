package com.ivandsky.puzzlebox.compete.create.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.NewDuelKey
import com.ivandsky.puzzlebox.compete.create.DuelChallenge
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * The New duel screen: the [key]'s challenge, if it has one, fills the form in. Back, Cancel and Done pop; a duel
 * against Past you replaces this screen with its own, so Back from that duel lands on Duels.
 */
@Composable
internal fun NewDuelRoute(key: NewDuelKey, navigator: Navigator) {
    val challenge = remember(key) { key.challenge() }
    NewDuelHost(
        challenge = challenge,
        onClose = navigator::pop,
        onOpened = { code -> navigator.replaceTop(DuelKey(code.value)) },
    )
}

private fun NewDuelKey.challenge(): DuelChallenge? =
    if (friendUid.isEmpty() || game.isEmpty()) null else DuelChallenge(friendUid, friendName, GameId(game))
