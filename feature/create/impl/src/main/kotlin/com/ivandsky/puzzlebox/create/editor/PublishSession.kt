package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.create.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.create.domain.PublishOutcome
import com.ivandsky.puzzlebox.create.domain.ValidatePublishDetails
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Studio's Publish: [publish] stores the pending edit first ([flush]), so the shared title,
 * details and test are the ones on screen, then runs [createShared]. A code it picks (none
 * reserved yet, or a taken one replaced) comes back through [reserved], the Studio's one
 * writer, and is stored by a [flush] before the next try goes out, so an autosave during the
 * call carries it and no other path writes the reserved code. One publish at a time.
 *
 * A publish that falls short (spec A15, A16) stays the outcome until the next one: offline or
 * refused, [State.retry] turns Publish into Retry, which goes out under the reserved code, so a
 * lost answer never doubles the puzzle. Nothing is queued: the session never publishes on its
 * own. At the store's daily cap (a server, UTC, day) the cap block keeps Publish off for as long
 * as the Studio is open; test solving stays on.
 *
 * The details (spec A15, A25): the author name and note as typed, checked by [validateDetails]
 * and handed to [storeDetails], the Studio's one writer. A draft with no author name shows the
 * account's display name, which is stored once published or edited; the name the author typed,
 * even an empty one, is never replaced. The level (96): the author may change the proposed one ([pickLevel],
 * [PublishLevel]); a pick that differs from the proposal is handed to [storeLevel], the Studio's writer, and the doc carries it, else the proposal.
 *
 * Publishing needs an account. Signed out, [publish] opens the sign-in sheet ([State.signIn]) holding the
 * request; [signIn] trades Google's ID token with [identity] and, signed in, fills the author name and runs the
 * held publish. [cancelSignIn] drops it; a failed sign-in reports [State.signInFailure]. An expired session
 * (SignedOut from the backend) opens the sheet once more; a second one, right after signing in, is the outcome.
 *
 * Confined to the main thread, like the Studio's other holders.
 */
class PublishSession(
    private val draftId: String,
    private val createShared: CreateSharedPuzzle,
    private val identity: IdentityService,
    private val idTokens: GoogleIdTokenProvider,
    private val scope: CoroutineScope,
    private val flush: suspend () -> Unit,
    private val reserved: (code: String) -> Unit,
    private val storeDetails: (authorName: String, note: String) -> Unit,
    private val storeLevel: (Level?) -> Unit,
    private val validateDetails: ValidatePublishDetails = ValidatePublishDetails(),
) {

    /**
     * [busy] while a publish runs; [outcome] is the last one's. [signedIn] names the Publish action; [signIn] is the
     * open sign-in sheet; [signInFailure] the last sign-in's failure until shown ([dismissSignInFailure]). [details] are
     * the inline fields. [reopened] when Published came from opening an already published draft.
     */
    data class State(
        val busy: Boolean = false,
        val outcome: PublishOutcome? = null,
        val signedIn: Boolean = true,
        val signIn: SignIn? = null,
        val signInFailure: SignInFailure? = null,
        val details: Details = Details(),
        val reopened: Boolean = false,
    ) {
        /** What keeps the draft from publishing here: the details' blocks, then the day's cap once the store answered it. */
        val blocks: List<PublishBlock>
            get() = if (outcome == PublishOutcome.DailyCapReached) details.blocks + DailyCapBlock else details.blocks

        /**
         * The last publish fell short in a way another try may fix: offline, a wrong clock, or
         * refused.
         */
        val retry: Boolean
            get() = outcome == PublishOutcome.Offline || outcome == PublishOutcome.ClockSkew ||
                outcome is PublishOutcome.Rejected
    }

    /**
     * The author name and note as typed; [blocks] are what keeps them from publishing. [visibility] is the Public or Link
     * only choice. [level] is the author's own pick over the proposal, null to follow it; [levelOffered] when the level
     * can be changed here (Public chosen).
     */
    data class Details(
        val authorName: String = "",
        val note: String = "",
        val blocks: List<PublishBlock> = emptyList(),
        val visibility: PublishVisibility.Choice = PublishVisibility.Choice(Visibility.Public),
        val level: Level? = null,
        val levelOffered: Boolean = false,
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

    /** The Public or Link only choice of this open publish. */
    private val visibility = PublishVisibility()

    private val levels = PublishLevel(visibility)

    private val mutableState = MutableStateFlow(
        State(
            signedIn = identity.current.value != null,
            details = Details(visibility = visibility.choice, levelOffered = levels.offered),
        ),
    )
    val state: StateFlow<State> = mutableState.asStateFlow()

    init {
        scope.launch {
            identity.current.collect { who ->
                mutableState.update { it.copy(signedIn = who != null) }
                prefillAuthor(who)
            }
        }
    }

    /**
     * The stored draft's [authorName] and [note], once it has loaded, and the code it was already
     * [published] under (null when it never was): a published draft reopens published, so it
     * stays read-only and shows its ticket. [level] is the author's kept level pick, null when none.
     */
    fun open(authorName: String, note: String, published: ShareCode?, level: Level? = null) {
        if (published != null) mutableState.update { it.copy(outcome = PublishOutcome.Published(published), reopened = true) }
        authorTyped = authorName.isNotEmpty()
        stored = authorName to note
        mutableState.update { it.copy(details = it.details.copy(level = level)) }
        setDetails(authorName, note)
        prefillAuthor(identity.current.value)
    }

    /** The author typed [authorName]: it is theirs from now on, and stored. Published, it is refused. */
    fun editAuthorName(authorName: String) {
        if (published) return
        authorTyped = true
        setDetails(authorName, mutableState.value.details.note)
        store()
    }

    /** The author typed [note]; it is stored. Published, it is refused. */
    fun editNote(note: String) {
        if (published) return
        setDetails(mutableState.value.details.authorName, note)
        store()
    }

    /** The author picked [next]. A published or running publish, or a falling-short one awaiting its retry, refuses it. */
    fun pickVisibility(next: Visibility) {
        if (published || mutableState.value.busy || !visibility.pick(next)) return
        mutableState.update { it.copy(details = it.details.copy(visibility = visibility.choice, levelOffered = levels.offered)) }
    }

    /** The author picked [next] over [verdict]'s proposal: kept and stored unless it is the proposal. Refused like the visibility, or with no proposal. */
    fun pickLevel(next: Level, verdict: Verdict) {
        val now = mutableState.value
        if (published || now.busy || !now.details.levelOffered || now.details.visibility.locked) return
        val pick = (LevelChoice.of(verdict, null) ?: return).pickFor(next)
        if (pick == now.details.level) return
        mutableState.update { it.copy(details = it.details.copy(level = pick)) }
        storeLevel(pick)
    }

    /** Publishes [draft] as checked by [verdict], signing in first; a no-op while one runs, the sheet is open or it is published. */
    fun publish(draft: EditorDraft<*>, verdict: Verdict) {
        val now = mutableState.value
        if (now.busy || now.signIn != null || published) return
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

    /** A published puzzle is immutable: no new details, no second publish. */
    private val published: Boolean get() = mutableState.value.outcome is PublishOutcome.Published

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
            val outcome = createShared(draftId, request.draft, request.verdict, visibility.sent, levels.carried(request.verdict, mutableState.value.details.level), ::reserve)
            if (outcome == PublishOutcome.SignedOut && !afterSignIn) {
                mutableState.update { it.copy(busy = false) }
                askToSignIn(request)
            } else {
                mutableState.update { it.copy(busy = false, outcome = outcome) }
                if (visibility.attempted(mutableState.value.retry)) {
                    mutableState.update { it.copy(details = it.details.copy(visibility = visibility.choice)) }
                }
            }
        }
    }

    /**
     * The code [createShared] picked (none reserved yet, or a taken one replaced) goes through
     * [reserved] and the Studio's one writer, and is stored before this returns, so every
     * autosave from now on carries it (spec A15). Storing it behind the writer's back would let an
     * autosave of the older row wipe it.
     */
    private suspend fun reserve(code: ShareCode) {
        reserved(code.value)
        flush()
    }

    /** Shows [who]'s display name, clamped to the cap, while the author has none of their own. */
    private fun prefillAuthor(who: Identity?) {
        val details = mutableState.value.details
        if (who == null || authorTyped || published || details.authorName.isNotEmpty()) return
        setDetails(who.displayName.clampedTo(Draft.AUTHOR_MAX), details.note)
    }

    private fun setDetails(authorName: String, note: String) {
        mutableState.update { it.copy(details = it.details.copy(authorName = authorName, note = note, blocks = validateDetails(authorName, note))) }
    }

    private fun store() {
        val details = mutableState.value.details
        val next = details.authorName to details.note
        if (next == stored) return
        stored = next
        storeDetails(next.first, next.second)
    }
}
