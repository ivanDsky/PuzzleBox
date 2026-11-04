package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.GameSummary
import com.ivandsky.puzzlebox.model.ThemeMode

/** One row in the history teaser. Phase 0 renders the day only; scores land in Phase 1. */
data class HistoryDay(
    val isoDay: String,
    val headline: String,
    val gameName: String,
)

/**
 * The real Home screen (A12, A15): Today card, history teaser, account card.
 * Single-pane on compact widths; list-detail on expanded. The theme choice is hoisted
 * to the composition root — this screen only edits it. Destinations are hoisted to the
 * app shell (ticket 17): play today's Zip, open the full history, open the account
 * surface — no in-screen placeholder behavior. [game] is the featured game's
 * identity; game-title strings render from it and from [HistoryDay.gameName], never
 * from a hardcoded id.
 */
@Composable
fun HomeScreen(
    layout: HomeLayout,
    game: GameSummary,
    identity: Identity? = null,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onOpenHistory: () -> Unit = {},
    history: List<HistoryDay>,
    onPlayToday: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }

    // Cards float ABOVE the pane in both themes (ticket 11): white on cool paper in
    // light, lighter slate on near-black in dark. The M3 container ramp inverts its
    // direction between themes (light descends from white, dark ascends from
    // near-black), so the pane/card pairing flips with the resolved scheme — this
    // also stays correct when the user forces the opposite appearance.
    val scheme = MaterialTheme.colorScheme
    val cardsFloatLighter = scheme.surface.luminance() > scheme.surfaceContainerHighest.luminance()
    val paneColor = if (cardsFloatLighter) scheme.surfaceContainer else scheme.surface
    val cardColor = if (cardsFloatLighter) scheme.surface else scheme.surfaceContainerLow

    Box(modifier.fillMaxSize()) {
        if (layout == HomeLayout.TwoPane) {
            Row(Modifier.fillMaxSize()) {
                ListPane(
                    game = game,
                    identity = identity,
                    themeMode = themeMode,
                    onOpenTheme = { showThemeDialog = true },
                    onOpenHistory = onOpenHistory,
                    history = history,
                    selectedDay = selectedDay,
                    onDayClick = { selectedDay = it },
                    selectable = true,
                    onPlayToday = onPlayToday,
                    onOpenProfile = onOpenProfile,
                    paneColor = paneColor,
                    cardColor = cardColor,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                )
                DetailPane(
                    selected = selectedDay?.let { iso -> history.firstOrNull { it.isoDay == iso } },
                    game = game,
                    onPlayToday = onPlayToday,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                )
            }
        } else {
            ListPane(
                game = game,
                identity = identity,
                themeMode = themeMode,
                onOpenTheme = { showThemeDialog = true },
                onOpenHistory = onOpenHistory,
                history = history,
                selectedDay = null,
                onDayClick = {},
                selectable = false,
                onPlayToday = onPlayToday,
                onOpenProfile = onOpenProfile,
                paneColor = paneColor,
                cardColor = cardColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showThemeDialog) {
        ThemeDialog(
            current = themeMode,
            onSelect = onSetThemeMode,
            onDismiss = { showThemeDialog = false },
        )
    }
}
