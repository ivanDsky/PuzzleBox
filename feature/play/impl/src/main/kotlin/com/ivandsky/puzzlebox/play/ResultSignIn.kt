package com.ivandsky.puzzlebox.play

import android.content.Context
import com.ivandsky.puzzlebox.play.board.note
import com.ivandsky.puzzlebox.play.friend.FriendPuzzleBoard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Results rank row's sign-in: the board's Google button, and [note], what to tell the player when a try did not finish. */
internal class ResultSignIn(private val scope: CoroutineScope, private val board: FriendPuzzleBoard) {

    val note: StateFlow<String?> = board.signInStatus.map { it.note() }.stateIn(scope, SharingStarted.Eagerly, null)

    fun tap(activityContext: Context) {
        scope.launch { board.signIn(activityContext) }
    }
}
