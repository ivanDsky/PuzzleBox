package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.insights.domain.recap.FriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.util.Locale
import kotlin.math.roundToLong

/**
 * The Best game among friends page, in the state its friends read came to: the game that earned the most points
 * among friends, a quiet line while the read is out, a note that this month's results settle on the 4th (A21), or "Can't load right now" in the card's own
 * place when the read could not finish (A16). The page holds its place from the start of the story and fills in when the read
 * answers.
 */
@Composable
internal fun FriendsCardPage(
    card: RecapCard.Friends,
    periodLabel: String,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
) {
    val state = card.state
    val loaded = state as? FriendsStanding.Loaded
    val settling = state as? FriendsStanding.Settling
    val names = loaded?.friendNames ?: settling?.friendNames.orEmpty()
    val friends = loaded?.friendCount ?: settling?.friendCount ?: 0
    val clock = rememberFriendsClock(crowdSize(friends), answered = loaded != null || settling != null)
    val ink = PaletteEntry.Friends.ink
    BoxWithConstraints(modifier.fillMaxSize().testTag("recap.friends")) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = periodLabel, style = MaterialTheme.typography.titleMedium, color = ink)
            Text(text = FriendsTitle, style = MaterialTheme.typography.titleLarge, color = ink, textAlign = TextAlign.Center)
            Box(Modifier.weight(1f).heightIn(min = StageMin), contentAlignment = Alignment.Center) {
                FriendsCrowd(names, friends, state.crowdState(), clock)
                when {
                    loaded != null -> FriendsBadge(loaded.game, clock.started)
                    settling != null -> SettlingTile(settling.settlesOn, clock.started)
                }
            }
            Column(
                Modifier.semantics(mergeDescendants = true) {}.testTag("recap.friends.${state.tag()}"),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state) {
                    FriendsStanding.Reading -> Text(ReadingHeadline, style = MaterialTheme.typography.headlineSmall, color = ink, textAlign = TextAlign.Center)
                    is FriendsStanding.Loaded -> {
                        KineticName(gameName(state.game), clock.started, Modifier.testTag("recap.friends.game"))
                        FriendsPoints(state.points.roundToLong(), state.pointsUnit(), clock.started, Modifier.testTag("recap.friends.points"))
                        if (state.weeks != null) {
                            Text(weeksNote(state.weeks), Modifier.testTag("recap.friends.weeks"), style = MaterialTheme.typography.bodyLarge, color = ink, textAlign = TextAlign.Center)
                        } else {
                            FriendsChip(names, state.friendCount, clock.started)
                        }
                    }
                    is FriendsStanding.Settling -> {
                        Text(state.headline(), style = MaterialTheme.typography.headlineMedium, color = ink, textAlign = TextAlign.Center)
                        Text(SettlingNote, style = MaterialTheme.typography.bodyLarge, color = ink, textAlign = TextAlign.Center)
                        if (state.friendCount > 0) FriendsChip(names, state.friendCount, clock.started)
                    }
                    FriendsStanding.Unavailable -> {
                        Text(UnavailableHeadline, style = MaterialTheme.typography.headlineMedium, color = ink, textAlign = TextAlign.Center)
                        Text(UnavailableNote, style = MaterialTheme.typography.bodyLarge, color = ink, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

private val StageMin = 240.dp

private fun FriendsStanding.Shown.crowdState(): CrowdState = when (this) {
    FriendsStanding.Reading -> CrowdState.Reading
    is FriendsStanding.Loaded -> CrowdState.Loaded
    is FriendsStanding.Settling -> CrowdState.Settling
    FriendsStanding.Unavailable -> CrowdState.Unavailable
}

/** What follows the number: a month's points are its total, a year's the average a day (A12). */
private fun FriendsStanding.Loaded.pointsUnit(): String = if (weeks == null) "pts among friends" else "pts a day among friends"

/** The Best game among friends fact in the tablet list: the title, then what the story page says in a line. */
@Composable
internal fun FriendsCardFact(
    card: RecapCard.Friends,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = FriendsTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        when (val state = card.state) {
            is FriendsStanding.Loaded -> {
                RecapFactRows(
                    rows = listOf(RecapFactRow("recap.friends.fact.game", gameName(state.game), state.pointsText())),
                    tag = "recap.friends.fact",
                )
                state.weeks?.let { weeks -> FriendsFactNote(weeksNote(weeks)) }
            }
            FriendsStanding.Reading -> FriendsFactNote(ReadingHeadline)
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
private const val ReadingHeadline = "Checking how your friends did\u2026"
private const val UnavailableHeadline = "Can't load right now"
private const val UnavailableNote = "Friends' results need a connection. The rest of your recap is ready."
private const val SettlingNote = "Friends can still add to this month, so the best game shows once results are final."

private fun FriendsStanding.Shown.tag(): String = when (this) {
    FriendsStanding.Reading -> "reading"
    is FriendsStanding.Loaded -> "loaded"
    is FriendsStanding.Settling -> "settling"
    FriendsStanding.Unavailable -> "unavailable"
}

/** A month's points are the window's total; a year's are the average a day over its kept weeks (A12). */
private fun FriendsStanding.Loaded.pointsText(): String =
    String.format(Locale.ROOT, if (weeks == null) "%,.0f pts" else "%,.0f pts a day", points)

private fun weeksNote(weeks: Int): String = "Based on $weeks weeks"

private fun FriendsStanding.Settling.headline(): String = "Settles on the ${settlesOn.dayOfMonth.ordinal()}"

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
