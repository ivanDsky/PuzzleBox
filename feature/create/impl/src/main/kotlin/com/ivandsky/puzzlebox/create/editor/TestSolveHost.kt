package com.ivandsky.puzzlebox.create.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.create.TestSolvePlaySlot
import com.ivandsky.puzzlebox.gameui.PlayLayout

/**
 * The open test solve in place of the Studio. With [regularPlay] and a [slot] it is the game's regular play screen
 * (ticket 98): the bar names the draft ([title]) and reads "Test solve" where a daily reads its number, back and the
 * bar's Up close the test unsaved, and the solve closes it once its sweep has played: [TestSolveSession] has stored
 * the test by then, so the Studio comes back tested. Otherwise it is the board-only [TestSolveScreen], as before.
 *
 * [modifier] is the route's: the screen is its own node under it, so both the route's and the screen's tags survive.
 */
@Composable
internal fun TestSolveHost(
    sandbox: TestSolveSession.Sandbox,
    title: String,
    layout: PlayLayout,
    regularPlay: Boolean,
    slot: TestSolvePlaySlot?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (regularPlay && slot != null) {
            BackHandler(onBack = onClose)
            slot.content(sandbox.ui, sandbox.controller, title, TEST_SOLVE, layout, onClose, onClose, Modifier)
        } else {
            TestSolveScreen(
                result = sandbox.result,
                onBack = onClose,
                board = { boardModifier -> sandbox.ui.Board(sandbox.controller, boardModifier) },
            )
        }
    }
}

/** What the play bar reads after the draft's name. */
private const val TEST_SOLVE = "Test solve"
