package com.ivandsky.puzzlebox.play.community.shelves.seeall

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.ReportResult
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.board.BoardSignInHolder
import com.ivandsky.puzzlebox.play.community.CommunityNotice
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a Report leaves on the page: the snackbar [notice], and the sign-in dialog a signed-out Report opens. */
internal data class SeeAllReportState(
    val notice: CommunityNotice? = null,
    val signInPrompt: Boolean = false,
    val signIn: BoardSignIn = BoardSignIn.Idle,
)

/**
 * The row ⋮ Report of a See all page, the same flow as New today's: [report] hides the puzzle on this device and says
 * so (through [onHidden], so the page drops it); signed out it asks to sign in and the player taps Report again.
 */
internal class SeeAllReporter(
    private val scope: CoroutineScope,
    private val reportPuzzle: ReportPuzzle,
    private val identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val onHidden: (ShareCode) -> Unit,
) {
    private val signInHolder = BoardSignInHolder(identity, idTokens)
    private val reporting = mutableSetOf<ShareCode>()
    private var noticeCount = 0
    private val current = MutableStateFlow(SeeAllReportState())

    val state: StateFlow<SeeAllReportState> = current.asStateFlow()

    init {
        scope.launch { signInHolder.status.collect { status -> current.update { it.copy(signIn = status) } } }
    }

    fun report(code: ShareCode) {
        if (!reporting.add(code)) return
        scope.launch {
            try {
                when (reportPuzzle(code)) {
                    ReportResult.Reported, ReportResult.AlreadyReported -> {
                        onHidden(code)
                        say("Reported. It's hidden on this device.")
                    }
                    ReportResult.SignedOut -> current.update { it.copy(signInPrompt = true) }
                    ReportResult.Offline -> say("You're offline. Report it when you're back.")
                    is ReportResult.Rejected -> say("Couldn't send the report. Try again later.")
                }
            } finally {
                reporting -= code
            }
        }
    }

    /** The sign-in dialog's Google button; [activityContext] hosts Google's account picker and is not kept. */
    fun signIn(activityContext: Context) {
        scope.launch {
            signInHolder.signIn(activityContext)
            if (identity.current.value != null) current.update { it.copy(signInPrompt = false) }
        }
    }

    fun dismissSignIn() = current.update { it.copy(signInPrompt = false) }

    /** The snackbar for notice [id] has been and gone. */
    fun noticeShown(id: Int) = current.update { if (it.notice?.id == id) it.copy(notice = null) else it }

    private fun say(message: String) = current.update { it.copy(notice = CommunityNotice(++noticeCount, message)) }
}
