package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.domain.PublishOutcome
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Studio's Publish: [publish] stores the pending edit first ([flush]), so the shared title
 * and test are the ones on screen, then runs [createShared]. The use case writes the reserved
 * code onto the stored row; [reserved] hands it back to the Studio's one writer, so a later
 * autosave keeps it. One publish at a time.
 *
 * Publishing needs an account. Signed out, [publish] stores nothing and opens the sign-in sheet
 * ([State.signIn]) holding the request; [signIn] asks Google for an ID token, trades it with
 * [identity] and, signed in, runs the held publish with no second tap. [cancelSignIn] drops it.
 * A failed sign-in closes the sheet, drops the request and reports [State.signInFailure]. The
 * backend may still answer SignedOut to a signed-in author (an expired session): the sheet opens
 * once more, and a second SignedOut, right after signing in, is shown as the outcome.
 *
 * Confined to the main thread, like the Studio's other holders.
 */
class PublishSession(
    private val draftId: String,
    private val createShared: CreateSharedPuzzle,
    private val drafts: DraftRepository,
    private val identity: IdentityService,
    private val idTokens: GoogleIdTokenProvider,
    private val scope: CoroutineScope,
    private val flush: suspend () -> Unit,
    private val reserved: (code: String?) -> Unit,
) {

    /**
     * [busy] while a publish runs; [outcome] is the last one's, null before the first.
     * [signedIn] names the Publish action; [signIn] is the open sign-in sheet, null when closed;
     * [signInFailure] is the last sign-in's failure until it is shown ([dismissSignInFailure]).
     */
    data class State(
        val busy: Boolean = false,
        val outcome: PublishOutcome? = null,
        val signedIn: Boolean = true,
        val signIn: SignIn? = null,
        val signInFailure: SignInFailure? = null,
    )

    /** The open sign-in sheet; [signingIn] while Google or the account exchange answers. */
    data class SignIn(val signingIn: Boolean = false)

    /** Why a sign-in didn't end signed in. */
    sealed interface SignInFailure {
        /** Google gave no ID token: the Credential Manager half. */
        data class Token(val reason: GoogleIdToken.Unavailable.Reason) : SignInFailure

        /** Google's token was refused or never reached the account backend. */
        data class Account(val error: AuthError) : SignInFailure
    }

    private class Request(val draft: EditorDraft<*>, val verdict: Verdict)

    /** The publish the open sheet holds; its identity tells a stale sign-in from the current one. */
    private var held: Request? = null

    private val mutableState = MutableStateFlow(State(signedIn = identity.current.value != null))
    val state: StateFlow<State> = mutableState.asStateFlow()

    init {
        scope.launch {
            identity.current.collect { who -> mutableState.update { it.copy(signedIn = who != null) } }
        }
    }

    /** Publishes [draft] as checked by [verdict], signing in first; a no-op while one runs or the sheet is open. */
    fun publish(draft: EditorDraft<*>, verdict: Verdict) {
        val now = mutableState.value
        if (now.busy || now.signIn != null) return
        val request = Request(draft, verdict)
        if (identity.current.value == null) askToSignIn(request) else run(request, afterSignIn = false)
    }

    /** The sheet's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    fun signIn(activityContext: Context) {
        val open = mutableState.value.signIn ?: return
        if (open.signingIn) return
        val request = held ?: return
        mutableState.update { it.copy(signIn = SignIn(signingIn = true)) }
        scope.launch {
            val failure = when (val token = idTokens.idToken(activityContext)) {
                is GoogleIdToken.Available -> when (val result = identity.signInWithGoogleIdToken(token.token)) {
                    is AuthResult.Ok -> null
                    is AuthResult.Err -> SignInFailure.Account(result.error)
                }
                is GoogleIdToken.Unavailable -> SignInFailure.Token(token.reason)
            }
            // Cancelled meanwhile: the sheet is gone or holds a newer request; nothing to continue or report.
            if (held !== request) return@launch
            held = null
            mutableState.update { it.copy(signIn = null, signInFailure = failure) }
            if (failure == null) run(request, afterSignIn = true)
        }
    }

    /** The sheet's Cancel or dismiss: closes it, publishing nothing. */
    fun cancelSignIn() {
        held = null
        mutableState.update { it.copy(signIn = null) }
    }

    /** The failure has been shown. */
    fun dismissSignInFailure() {
        mutableState.update { it.copy(signInFailure = null) }
    }

    private fun askToSignIn(request: Request) {
        held = request
        mutableState.update { it.copy(signIn = SignIn(), signInFailure = null) }
    }

    private fun run(request: Request, afterSignIn: Boolean) {
        mutableState.update { it.copy(busy = true, signInFailure = null) }
        scope.launch {
            flush()
            val outcome = createShared(draftId, request.draft, request.verdict)
            reserved(drafts.get(draftId)?.reservedCode)
            if (outcome == PublishOutcome.SignedOut && !afterSignIn) {
                mutableState.update { it.copy(busy = false) }
                askToSignIn(request)
            } else {
                mutableState.update { it.copy(busy = false, outcome = outcome) }
            }
        }
    }
}
