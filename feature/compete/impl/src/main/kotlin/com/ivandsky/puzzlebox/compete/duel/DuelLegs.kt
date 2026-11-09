package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import com.ivandsky.puzzlebox.gameui.rankRuleText
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val MILLIS_PER_MINUTE = 60_000L
private val AvatarSize = 40.dp
private val AvatarOverlap = 28.dp
private val EndsFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())

/** What the screen's buttons do: [onOpenLeg] starts or resumes a puzzle, [onNudge] shares the text, [onReveal] opens the reveal. */
internal data class LegsActions(
    val onOpenLeg: (LegRowModel) -> Unit,
    val onNudge: (String) -> Unit,
    val onReveal: () -> Unit,
)

/**
 * The duel's puzzles for a seat holder: header, one card per puzzle, then the footer. [layout] `TwoPane` puts the
 * list on the left and the selected puzzle's detail (with its Start button) on the right. The countdown text is the
 * only thing here that reads the clock; it wakes when the minutes change.
 */
@Composable
internal fun DuelLegs(
    state: DuelUiState.Participant,
    layout: PlayLayout,
    actions: LegsActions,
    modifier: Modifier = Modifier,
) {
    val view = state.view
    val games = koinInject<GameRegistry>()
    val brands = koinInject<GamePlayRegistry>()
    val now by rememberCountdownNow(view.match.closesAt)
    val model = remember(view, state.elapsedMs, now) {
        duelLegsModel(view, state.elapsedMs, now, games::displayName, games::rankOrderOf)
    }
    val content = LegsContent(model, view.match.deadline, view.match.closesAt, view.match.code, now, actions)
    if (layout == PlayLayout.TwoPane) {
        TwoPaneLegs(content, brands, modifier)
    } else {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            LegsHeader(content)
            PuzzleList(content, brands, selected = null, onSelect = null)
            LegsFooter(content)
        }
    }
}

/** Everything the panes draw, so the pane composables take one value. */
private class LegsContent(
    val model: DuelLegsModel,
    val deadline: Instant,
    val closesAt: Instant,
    val code: ShareCode,
    val now: Instant,
    val actions: LegsActions,
)

@Composable
private fun TwoPaneLegs(content: LegsContent, brands: GamePlayRegistry, modifier: Modifier) {
    val rows = content.model.rows
    var picked by rememberSaveable { mutableIntStateOf(-1) }
    val selected = rows.firstOrNull { it.legIndex == picked } ?: rows.firstOrNull { it.action != null } ?: rows.firstOrNull()
    Row(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            LegsHeader(content)
            PuzzleList(content, brands, selected = selected?.legIndex, onSelect = { picked = it })
            LegsFooter(content)
        }
        VerticalDivider()
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (selected != null) {
                LegDetail(selected, content.model.opponent.name, brands, onAction = { content.actions.onOpenLeg(selected) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegsHeader(content: LegsContent) {
    val model = content.model
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box {
                PlayerAvatar(model.me.name, model.me.photoUrl, size = AvatarSize)
                PlayerAvatar(
                    name = model.opponent.name,
                    photoUrl = model.opponent.photoUrl,
                    modifier = Modifier.padding(start = AvatarOverlap),
                    size = AvatarSize,
                    tone = DuelTone.Them,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${model.me.name} vs ${model.opponent.name}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("duel.legs.title"),
                )
                Text(
                    text = "Best of ${model.rows.size} · ends ${EndsFormat.format(content.deadline.atZone(ZoneId.systemDefault()))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Pill(timeLeftText(content.closesAt, content.now), Modifier.testTag("duel.legs.timeLeft"))
            Pill("Score hidden until it settles")
        }
    }
}

@Composable
private fun PuzzleList(content: LegsContent, brands: GamePlayRegistry, selected: Int?, onSelect: ((Int) -> Unit)?) {
    val rows = content.model.rows
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Puzzles", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(rows.size.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    rows.forEach { row ->
        DuelLegCard(
            row = row,
            opponentName = content.model.opponent.name,
            brand = brands.brandOf(row.gameId),
            selected = row.legIndex == selected,
            onClick = onSelect?.let { select -> { select(row.legIndex) } },
            onAction = if (onSelect == null) ({ content.actions.onOpenLeg(row) }) else null,
        )
    }
}

/** The tablet's right pane: the selected puzzle large, with the Start / Resume button moved here from its card. */
@Composable
private fun LegDetail(row: LegRowModel, opponentName: String, brands: GamePlayRegistry, onAction: () -> Unit) {
    Column(Modifier.testTag("duel.legs.detail"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GameGlyph(brands.brandOf(row.gameId), GameGlyphSize.Hero)
        Text(row.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(rankRuleText(row.rankOrder), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LegStatusPill(row.status)
        OpponentText(row.opponent, opponentName)
        PostingNote(row.posting)
        LegActionButton(row, onAction, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LegsFooter(content: LegsContent) {
    val model = content.model
    val name = model.opponent.name
    when (model.footer) {
        DuelFooter.Playing -> Unit
        DuelFooter.Waiting -> FooterCard("Waiting on $name") {
            SecondaryButton(
                text = "Nudge",
                onClick = { content.actions.onNudge(nudgeText(name, content.code, content.closesAt, content.now)) },
                modifier = Modifier.testTag("duel.legs.nudge"),
            )
        }
        DuelFooter.RevealReady -> FooterCard(if (isClosed(content.closesAt, content.now)) "Closed" else "Both finished") {
            PrimaryButton("See the reveal", onClick = content.actions.onReveal, modifier = Modifier.testTag("duel.legs.reveal"))
        }
    }
}

@Composable
private fun FooterCard(title: String, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(Spacing.md))
            .padding(Spacing.md)
            .testTag("duel.legs.footer"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

/** The time for the countdown text, refreshed whenever the minutes left change, until [closesAt] has passed. */
@Composable
private fun rememberCountdownNow(closesAt: Instant): State<Instant> = produceState(Instant.now(), closesAt) {
    while (true) {
        val current = Instant.now()
        value = current
        val left = Duration.between(current, closesAt).toMillis()
        if (left <= 0L) break
        delay(left % MILLIS_PER_MINUTE + 1)
    }
}
