package com.ivandsky.puzzlebox.app.screens

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.app.auth.GoogleIdToken
import com.ivandsky.puzzlebox.app.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Profile (ticket 16): Google sign-in state (A6) plus the per-day leaderboard v1.
 * Signed out is a first-class, clearly-prompted state (A7/A15) — nothing else in the
 * app is gated on identity.
 */
data class ProfileUiState(
    val identity: Identity? = null,
    val signingIn: Boolean = false,
    val signInMessage: String? = null,
    /** Null while no board is observable — see [boardUnavailable] for the distinction. */
    val board: LeaderboardSnapshot? = null,
    val boardLoading: Boolean = false,
    val boardUnavailable: Boolean = false,
)

class ProfileViewModel(
    private val identityService: IdentityService,
    private val idTokenProvider: GoogleIdTokenProvider,
    private val leaderboard: LeaderboardRepository,
    private val todayBoard: () -> LeaderboardRef,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var boardJob: Job? = null

    init {
        viewModelScope.launch {
            identityService.current.collect { identity ->
                _state.update {
                    it.copy(
                        identity = identity,
                        board = null,
                        boardLoading = identity != null,
                        boardUnavailable = false,
                    )
                }
                boardJob?.cancel()
                if (identity != null) {
                    boardJob = viewModelScope.launch {
                        leaderboard.observe(todayBoard()).collect { snapshot ->
                            _state.update {
                                it.copy(
                                    board = snapshot,
                                    boardLoading = false,
                                    // A7: a null snapshot is the graceful "not right now",
                                    // shown as a soft state, never an error.
                                    boardUnavailable = snapshot == null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun signIn() {
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, signInMessage = null) }
            val result = when (val token = idTokenProvider.idToken()) {
                is GoogleIdToken.Available ->
                    identityService.signInWithGoogleIdToken(token.token)
                GoogleIdToken.Unavailable ->
                    AuthResult.Err(AuthError.Unavailable("No Google sign-in available"))
            }
            _state.update { current ->
                when (result) {
                    is AuthResult.Ok -> current.copy(signingIn = false, signInMessage = null)
                    is AuthResult.Err ->
                        current.copy(signingIn = false, signInMessage = friendly(result.error))
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { identityService.signOut() }
    }

    private fun friendly(error: AuthError): String = when (error) {
        is AuthError.Unavailable -> "Sign-in is unavailable right now. Check your connection."
        is AuthError.Rejected -> "Google refused the sign-in. Please try again."
        is AuthError.Malformed -> "The account response was unusable. Please try again."
        is AuthError.Unknown -> "Sign-in failed unexpectedly. Please try again."
    }
}

@Composable
fun ProfileRoute(
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    ProfileContent(
        state = state,
        onSignIn = viewModel::signIn,
        onSignOut = viewModel::signOut,
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
        val identity = state.identity
        if (identity == null) {
            SignedOutSection(state, onSignIn)
        } else {
            SignedInSection(state, identity, onSignOut)
        }
        HorizontalDivider()
        OutlinedButton(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) {
            Text("Design system catalog")
        }
    }
}

@Composable
private fun SignedOutSection(state: ProfileUiState, onSignIn: () -> Unit) {
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
    state: ProfileUiState,
    identity: Identity,
    onSignOut: () -> Unit,
) {
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

        LeaderboardSection(state, myUid = identity.uid)
    }
}

/** Leaderboard v1 (brief §3): today's board, name + time + hints, own row highlighted. */
@Composable
private fun LeaderboardSection(state: ProfileUiState, myUid: String) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("leaderboard"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Today's leaderboard",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when {
            state.boardLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp).testTag("leaderboard-loading"),
                )
            }
            state.board == null -> {
                Text(
                    text = if (state.boardUnavailable) {
                        "Leaderboard unavailable right now."
                    } else {
                        "Leaderboard not loaded yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.board.entries.isEmpty() -> {
                Text(
                    text = "No entries yet today — finish today's Zip to be first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                state.board.entries.forEachIndexed { index, entry ->
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
