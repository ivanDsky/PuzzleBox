package com.ivandsky.puzzlebox.app.screens

import android.content.Context
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.app.auth.GoogleIdToken
import com.ivandsky.puzzlebox.app.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.domain.ObserveTodayLeaderboard
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Profile (ticket 16): Google sign-in state (A6) plus the per-day leaderboard v1.
 * Signed out is a first-class, clearly-prompted state (A7/A15) — nothing else in the
 * app is gated on identity.
 */
sealed interface ProfileUiState {
    /** The sign-in prompt; [signingIn] while the credential flow runs, then its outcome copy. */
    data class SignedOut(
        val signingIn: Boolean = false,
        val signInMessage: String? = null,
    ) : ProfileUiState

    /** The player and today's board ([TodayLeaderboard.Board]: loading, loaded, or unavailable). */
    data class SignedIn(
        val identity: Identity,
        val board: TodayLeaderboard.Board,
    ) : ProfileUiState
}

// Ticket 24 — honest sign-in copy per failure class. "Check your connection" is
// reserved for the network branches; every other class says what actually failed.
private const val SIGN_IN_NETWORK_MESSAGE = "Sign-in is unavailable right now. Check your connection."
private const val SIGN_IN_GOOGLE_UNAVAILABLE_MESSAGE =
    "Google sign-in isn't available right now. Try again, or try another account."
private const val SIGN_IN_RETRY_MESSAGE = "Sign-in failed. Please try again."

/**
 * Profile's state holder: today's identity-gated board ([ObserveTodayLeaderboard]) plus
 * the sign-in flow's own progress/outcome, shown while signed out. The activity context
 * the credential sheet needs (I2) is passed per call and never stored.
 */
class ProfileViewModel(
    private val identityService: IdentityService,
    private val idTokenProvider: GoogleIdTokenProvider,
    observeTodayLeaderboard: ObserveTodayLeaderboard,
) : ViewModel() {

    private val signIn = MutableStateFlow(ProfileUiState.SignedOut())

    val uiState: StateFlow<ProfileUiState> =
        combine(observeTodayLeaderboard(), signIn) { today, prompt ->
            when (today) {
                TodayLeaderboard.SignedOut -> prompt
                is TodayLeaderboard.SignedIn -> ProfileUiState.SignedIn(today.identity, today.board)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = identityService.current.value
                ?.let { ProfileUiState.SignedIn(it, TodayLeaderboard.Board.Loading) }
                ?: signIn.value,
        )

    fun onSignIn(activityContext: Context) {
        viewModelScope.launch {
            signIn.update { it.copy(signingIn = true, signInMessage = null) }
            // Ticket 24: copy must match the failure class. A credential-provider
            // failure never reached the backend, so it is not an AuthError.Unavailable
            // (that class is the backend network path) — it maps straight to copy.
            val message: String? = when (val token = idTokenProvider.idToken(activityContext)) {
                is GoogleIdToken.Available ->
                    when (val result = identityService.signInWithGoogleIdToken(token.token)) {
                        is AuthResult.Ok -> null
                        is AuthResult.Err -> friendly(result.error)
                    }
                is GoogleIdToken.Unavailable -> friendly(token.reason)
            }
            signIn.update { it.copy(signingIn = false, signInMessage = message) }
        }
    }

    fun onSignOut() {
        viewModelScope.launch { identityService.signOut() }
    }

    private fun friendly(error: AuthError): String = when (error) {
        // Backend half: FirebaseNetworkException — the only branch allowed to blame
        // the connection (ticket 24).
        is AuthError.Unavailable -> SIGN_IN_NETWORK_MESSAGE
        // Token-exchange failure (invalid/expired token, account blocked): the honest
        // remediation is retry or another account, not "check your connection".
        is AuthError.Rejected -> SIGN_IN_GOOGLE_UNAVAILABLE_MESSAGE
        is AuthError.Malformed -> "The account response was unusable. Please try again."
        is AuthError.Unknown -> "Sign-in failed unexpectedly. Please try again."
    }

    /** Credential Manager half (ticket 24): copy chosen per preserved failure class. */
    private fun friendly(reason: GoogleIdToken.Unavailable.Reason): String = when (reason) {
        GoogleIdToken.Unavailable.Reason.NETWORK -> SIGN_IN_NETWORK_MESSAGE
        GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL,
        GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE,
        -> SIGN_IN_GOOGLE_UNAVAILABLE_MESSAGE
        GoogleIdToken.Unavailable.Reason.CANCELLED,
        GoogleIdToken.Unavailable.Reason.MALFORMED,
        GoogleIdToken.Unavailable.Reason.OTHER,
        -> SIGN_IN_RETRY_MESSAGE
    }
}

@Composable
fun ProfileRoute(
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The credential sheet launches from the current Activity's context (I2) —
    // read in composition (LocalContext is composable-scoped), handed per call.
    val activityContext = LocalContext.current
    ProfileContent(
        state = state,
        onSignIn = { viewModel.onSignIn(activityContext) },
        onSignOut = viewModel::onSignOut,
        onOpenCatalog = onOpenCatalog,
        modifier = modifier,
    )
}

@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            is ProfileUiState.SignedOut -> SignedOutSection(state, onSignIn)
            is ProfileUiState.SignedIn -> SignedInSection(state, onSignOut)
        }
        HorizontalDivider()
        OutlinedButton(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) {
            Text("Design system catalog")
        }
    }
}

@Composable
private fun SignedOutSection(state: ProfileUiState.SignedOut, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("profile-signed-out"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Sign in with Google to put your records on the daily leaderboard.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onSignIn, enabled = !state.signingIn) {
            Text(if (state.signingIn) "Signing in…" else "Sign in with Google")
        }
        state.signInMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SignedInSection(
    state: ProfileUiState.SignedIn,
    onSignOut: () -> Unit,
) {
    val identity = state.identity
    Column(
        modifier = Modifier.fillMaxWidth().testTag("profile-signed-in"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = identity.displayName.ifEmpty { "Player" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Signed in with Google",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }

        LeaderboardSection(state.board, myUid = identity.uid)
    }
}

/** Leaderboard v1 (brief §3): today's board, name + time + hints, own row highlighted. */
@Composable
private fun LeaderboardSection(board: TodayLeaderboard.Board, myUid: String) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("leaderboard"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Today's leaderboard",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when (board) {
            TodayLeaderboard.Board.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp).testTag("leaderboard-loading"),
                )
            }
            // A7: a null snapshot is the graceful "not right now", shown as a soft
            // state, never an error.
            TodayLeaderboard.Board.Unavailable -> {
                Text(
                    text = "Leaderboard unavailable right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is TodayLeaderboard.Board.Loaded -> if (board.snapshot.entries.isEmpty()) {
                Text(
                    text = "No entries yet today — finish today's Zip to be first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                board.snapshot.entries.forEachIndexed { index, entry ->
                    LeaderboardRow(
                        rank = index + 1,
                        entry = entry,
                        isSelf = entry.uid == myUid,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, isSelf: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelf) {
                    Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .testTag("leaderboard-row-self")
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.displayName.ifEmpty { "Player" },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelf) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        Text(
            text = "${formatTime(entry.timeMs)} · ${entry.hints} ${if (entry.hints == 1) "hint" else "hints"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Score contract display (A3): m:ss, hours only when they exist — same as history. */
private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
