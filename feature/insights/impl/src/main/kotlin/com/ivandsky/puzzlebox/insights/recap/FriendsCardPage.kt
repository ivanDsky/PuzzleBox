package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.FriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.util.Locale

/**
 * The Best game among friends page, in the state its friends read came to: the game that earned the most points
 * among friends, a note that this month's results settle on the 4th (A21), or "Can't load right now" in the card's own
 * place when the read could not finish (A16). The state arrives after the story opens, so this page is the one that
 * fills in late.
 */
@Composable
internal fun FriendsCardPage(
    card: RecapCard.Friends,
    periodLabel: String,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("recap.friends"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = FriendsTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Column(
            Modifier.padding(top = Spacing.xl).semantics(mergeDescendants = true) {}.testTag("recap.friends.${card.state.tag()}"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val state = card.state) {
                is FriendsStanding.Loaded -> {
                    Text(
                        text = gameName(state.game),
                        modifier = Modifier.testTag("recap.friends.game"),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "${state.pointsText()} among friends",
                        modifier = Modifier.testTag("recap.friends.points"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
                is FriendsStanding.Settling -> {
                    Text(
                        text = state.headline(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = SettlingNote,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                FriendsStanding.Unavailable -> {
                    Text(
                        text = UnavailableHeadline,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = UnavailableNote,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The Best game among friends fact in the tablet list: the title, then what the story page says in a line. */
@Composable
internal fun FriendsCardFact(
    card: RecapCard.Friends,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = FriendsTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
        when (val state = card.state) {
            is FriendsStanding.Loaded -> RecapFactRows(
                rows = listOf(RecapFactRow("recap.friends.fact.game", gameName(state.game), state.pointsText())),
                tag = "recap.friends.fact",
            )
            is FriendsStanding.Settling -> FriendsFactNote(state.headline())
            FriendsStanding.Unavailable -> FriendsFactNote(UnavailableHeadline)
        }
    }
}

@Composable
private fun FriendsFactNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("recap.friends.fact.note"),
    )
}

private const val FriendsTitle = "Best game among friends"
private const val UnavailableHeadline = "Can't load right now"
private const val UnavailableNote = "Friends' results need a connection. The rest of your recap is ready."
private const val SettlingNote = "Friends can still add to this month, so the best game shows once results are final."

private fun FriendsStanding.Shown.tag(): String = when (this) {
    is FriendsStanding.Loaded -> "loaded"
    is FriendsStanding.Settling -> "settling"
    FriendsStanding.Unavailable -> "unavailable"
}

private fun FriendsStanding.Loaded.pointsText(): String = String.format(Locale.ROOT, "%,.0f pts", points)

private fun FriendsStanding.Settling.headline(): String = "Results settle on the ${settlesOn.dayOfMonth.ordinal()}"

private fun Int.ordinal(): String {
    val suffix = if (this % 100 in 11..13) {
        "th"
    } else {
        when (this % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    return "$this$suffix"
}
