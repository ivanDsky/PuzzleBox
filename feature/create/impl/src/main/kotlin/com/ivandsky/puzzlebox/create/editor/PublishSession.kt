package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.domain.PublishOutcome
import com.ivandsky.puzzlebox.domain.ValidatePublishDetails
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Studio's Publish: [publish] stores the pending edit first ([flush]), so the shared title,
 * details and test are the ones on screen, then runs [createShared]. The use case writes the
 * reserved code onto the stored row; [reserved] hands it back to the Studio's one writer, so a
 * later autosave keeps it. One publish at a time.
 *
 * The details (spec A15, A25): the author name and note as typed, checked by [validateDetails]
 * and handed to [storeDetails], the Studio's one writer. A draft with no author name shows the
 * account's display name, which is stored once published or edited; the name the author typed,
 * even an empty one, is never replaced.
 *
 * Publishing needs an account. Signed out, [publish] stores nothing and opens the sign-in sheet
 * ([State.signIn]) holding the request; [signIn] asks Google for an ID token, trades it with
 * [identity] and, signed in, fills the author name and runs the held publish with no second tap.
 * [cancelSignIn] drops it. A failed sign-in closes the sheet, drops the request and reports
 * [State.signInFailure]. The backend may still answer SignedOut to a signed-in author (an
 * expired session): the sheet opens once more, and a second SignedOut, right after signing in,
 * is shown as the outcome.
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
    private val storeDetails: (authorName: String, note: String) -> Unit,
    private val validateDetails: ValidatePublishDetails = ValidatePublishDetails(),
) {

    /**
     * [busy] while a publish runs; [outcome] is the last one's, null before the first.
     * [signedIn] names the Publish action; [signIn] is the open sign-in sheet, null when closed;
     * [signInFailure] is the last sign-in's failure until it is shown ([dismissSignInFailure]).
     * [details] are the inline author name and note.
     */
    data class State(
        val busy: Boolean = false,
        val outcome: PublishOutcome? = null,
        val signedIn: Boolean = true,
        val signIn: SignIn? = null,
        val signInFailure: SignInFailure? = null,
        val details: Details = Details(),
    )

    /** The author name and note as typed; [blocks] are what keeps them from publishing. */
    data class Details(
        val authorName: String = "",
        val note: String = "",
        val blocks: List<PublishBlock> = emptyList(),
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

    /** True once the author name is the author's own: stored non-empty, or edited here. */
    private var authorTyped = false

    /** The author name and note on the stored row, as last handed to [storeDetails]. */
    private var stored: Pair<String, String> = "" to ""

    private val mutableState = MutableStateFlow(State(signedIn = identity.current.value != null))
    val state: StateFlow<State> = mutableState.asStateFlow()

    init {
        scope.launch {
            identity.current.collect { who ->
                mutableState.update { it.copy(signedIn = who != null) }
                prefillAuthor(who)
            }
        }
    }

    /** The stored draft's [authorName] and [note], once it has loaded. */
    fun open(authorName: String, note: String) {
        authorTyped = authorName.isNotEmpty()
        stored = authorName to note
        setDetails(authorName, note)
        prefillAuthor(identity.current.value)
    }

    /** The author typed [authorName]: it is theirs from now on, and stored. */
    fun editAuthorName(authorName: String) {
        authorTyped = true
        setDetails(authorName, mutableState.value.details.note)
        store()
    }

    /** The author typed [note]; it is stored. */
    fun editNote(note: String) {
        setDetails(mutableState.value.details.authorName, note)
        store()
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
                    is AuthResult.Ok -> {
                        prefillAuthor(result.identity)
                        null
                    }
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
        // A display name shown but not yet stored goes out with this publish.
        store()
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

    /** Shows [who]'s display name, clamped to the cap, while the author has none of their own. */
    private fun prefillAuthor(who: Identity?) {
        val details = mutableState.value.details
        if (who == null || authorTyped || details.authorName.isNotEmpty()) return
        setDetails(who.displayName.clampedTo(Draft.AUTHOR_MAX), details.note)
    }

    private fun setDetails(authorName: String, note: String) {
        val details = Details(authorName, note, validateDetails(authorName, note))
        mutableState.update { it.copy(details = details) }
    }

    private fun store() {
        val details = mutableState.value.details
        val next = details.authorName to details.note
        if (next == stored) return
        stored = next
        storeDetails(next.first, next.second)
    }
}

/** This string trimmed and cut to at most [max] code points, never splitting a character. */
private fun String.clampedTo(max: Int): String {
    val trimmed = trim()
    return if (trimmed.codePointCount(0, trimmed.length) <= max) trimmed else trimmed.substring(0, trimmed.offsetByCodePoints(0, max))
}
