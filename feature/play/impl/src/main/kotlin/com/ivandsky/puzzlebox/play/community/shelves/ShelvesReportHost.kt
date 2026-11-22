package com.ivandsky.puzzlebox.play.community.shelves

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.play.community.CommunitySignInDialog
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllNotice
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllReportState

/**
 * What a card's Report on the shelves home leaves over it: the notice ("Reported. It's hidden on this device.", ending
 * [bottomPadding] above the bottom so it clears the create button) and the sign-in dialog a signed-out Report opens.
 * [onNoticeShown] says a notice has been and gone; [onSignIn] runs the dialog's Google button over the activity's context.
 */
@Composable
internal fun ShelvesReportHost(
    state: SeeAllReportState,
    onNoticeShown: (Int) -> Unit,
    onSignIn: (Context) -> Unit,
    onDismissSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    Box(modifier) {
        SeeAllNotice(state.notice, onNoticeShown, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
        if (state.signInPrompt) CommunitySignInDialog(state.signIn, onSignIn = { onSignIn(context) }, onDismiss = onDismissSignIn)
    }
}
