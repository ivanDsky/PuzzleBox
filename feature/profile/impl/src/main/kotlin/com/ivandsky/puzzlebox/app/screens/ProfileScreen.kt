package com.ivandsky.puzzlebox.app.screens

import androidx.compose.foundation.background
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.profile.AppearanceRow
import com.ivandsky.puzzlebox.profile.board.GameRow
import com.ivandsky.puzzlebox.profile.board.PerGameBoard
import com.ivandsky.puzzlebox.profile.GameRows
import com.ivandsky.puzzlebox.profile.LifetimeGrid
import com.ivandsky.puzzlebox.profile.ProfileHeader
import com.ivandsky.puzzlebox.profile.board.ProfileDailyBoard
import java.time.LocalDate

/**
 * Profile (A20), top to bottom: the name and "Playing since", the lifetime grid, one row
 * per game ([onOpenHistory], N3), the account (sign-in and today's board), Appearance
 * (N4), and — only when [onOpenCatalog] is given (debug builds) — the catalog link.
 *
 * [slim] keeps only the header and the account line: it hides the lifetime grid, the game
 * rows, today's board, Appearance and the catalog link. A non-null [onBack] draws a top
 * bar with a back icon at the left, in either form, and a non-null [onOpenSettings] adds
 * a gear at its right.
 */
@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onOpenHistory: (GameId) -> Unit,
    onSelectGame: (GameId) -> Unit,
    onOpenCatalog: (() -> Unit)?,
    modifier: Modifier = Modifier,
    slim: Boolean = false,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val account = state.account
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    if (onBack != null) {
        CompactTopBar(
            title = "Profile",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("profile.back")) {
                    Icon(
                        imageVector = PuzzleIcons.Back,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                if (onOpenSettings != null) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("profile.settings")) {
                        Icon(
                            imageVector = PuzzleIcons.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        val name = when (account) {
            is ProfileUiState.Account.SignedIn -> account.identity.displayName.ifEmpty { "Player" }
            is ProfileUiState.Account.SignedOut -> "Guest player"
        }
        ProfileHeader(name = name, playingSince = state.stats.playingSince)
        if (!slim) {
            LifetimeGrid(stats = state.stats, daysPlayed = state.daysPlayed)
            if (state.games.isNotEmpty()) {
                GameRows(games = state.games, onOpenHistory = onOpenHistory)
            }
            HorizontalDivider()
        }
        when (account) {
            is ProfileUiState.Account.SignedOut -> SignedOutSection(account, onSignIn)
            is ProfileUiState.Account.SignedIn ->
                SignedInSection(account, onSignOut, state.games, state.perGameBoard, onSelectGame, showBoard = !slim)
        }
        if (!slim) {
            HorizontalDivider()
            AppearanceRow(current = state.themeMode, onSelect = onSetThemeMode)
            if (onOpenCatalog != null) {
                SecondaryButton(
                    text = "Design system catalog",
                    onClick = onOpenCatalog,
                    modifier = Modifier.fillMaxWidth().testTag("profile.catalog"),
                )
            }
        }
    }
    }
}

@Composable
private fun SignedOutSection(account: ProfileUiState.Account.SignedOut, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("profile-signed-out"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Sign in with Google to put your times on the leaderboards.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onSignIn, enabled = !account.signingIn) {
            Text(if (account.signingIn) "Signing in…" else "Sign in with Google")
        }
        account.signInMessage?.let { message ->
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
    account: ProfileUiState.Account.SignedIn,
    onSignOut: () -> Unit,
    games: List<GameRow>,
    perGameBoard: PerGameBoard,
    onSelectGame: (GameId) -> Unit,
    showBoard: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("profile-signed-in"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Signed in with Google",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(text = "Sign out", onClick = onSignOut)
        }
        if (showBoard) {
            ProfileDailyBoard(games = games, board = perGameBoard, onSelect = onSelectGame)
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ProfileContentPreview() {
    PuzzleBoxTheme {
        ProfileContent(
            state = ProfileUiState(
                account = ProfileUiState.Account.SignedIn(
                    identity = Identity(uid = "u1", displayName = "Ann", photoUrl = null),
                ),
                stats = ProfileUiState.NO_STATS.copy(
                    solved = 42,
                    bestStreak = 9,
                    hintFree = 30,
                    playingSince = LocalDate.of(2026, 3, 1),
                ),
                daysPlayed = 21,
                games = listOf(
                    GameRow(
                        GameId("zip"), "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip), solved = 20,
                        best = BestScore(41_000, hints = 0), rankOrder = RankOrder.TimeThenHints,
                    ),
                    GameRow(
                        GameId("strands"), "Strands", GameBrand(AccentSlot.Pink, GameGlyphs.Strands), solved = 22,
                        best = BestScore(185_000, hints = 1), rankOrder = RankOrder.HintsThenTime,
                    ),
                    GameRow(
                        GameId("tango"), "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango), solved = 0,
                        best = null, rankOrder = RankOrder.TimeThenHints,
                    ),
                ),
                themeMode = ThemeMode.DARK,
                perGameBoard = PerGameBoard(
                    GameId("zip"),
                    RankOrder.TimeThenHints,
                    DailyBoardState.Unavailable,
                ),
            ),
            onSignIn = {},
            onSignOut = {},
            onSetThemeMode = {},
            onOpenHistory = {},
            onSelectGame = {},
            onOpenCatalog = {},
        )
    }
}
