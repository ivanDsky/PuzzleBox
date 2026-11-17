package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.common.PastYouAvatar
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.MaxContentWidth
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.TwoColumnFrame
import com.ivandsky.puzzlebox.designsystem.TwoColumnMinWidth
import com.ivandsky.puzzlebox.designsystem.maxContentWidth
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import com.ivandsky.puzzlebox.gameui.rankRuleText
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import org.koin.compose.koinInject

private val AvatarSize = 40.dp
private val AvatarOverlap = 28.dp

/**
 * What the screen's buttons do: [onOpenLeg] starts or resumes a puzzle, [onNudge] shares the text, [onReveal] opens
 * the reveal, [onRemove] asks to remove a self duel.
 */
internal data class LegsActions(
    val onOpenLeg: (LegRowModel) -> Unit,
    val onNudge: (String) -> Unit,
    val onReveal: () -> Unit,
    val onRemove: () -> Unit,
)

/**
 * The duel's puzzles for a seat holder: header, one card per puzzle, then the footer. [layout] `TwoPane`, in room for
 * two columns ([TwoColumnMinWidth]), puts the list on the left and the selected puzzle's detail (with its Start button) in
 * the fixed column on the right, laid out as the friend page is; a narrower pane keeps the one column, capped at
 * [MaxContentWidth]. [now] is the clock the countdown, Nudge and footer read; the caller owns the ticker. [time] writes the deadline.
 */
@Composable
internal fun DuelLegs(
    state: DuelUiState.Participant,
    layout: PlayLayout,
    now: Instant,
    time: CompeteTime,
    actions: LegsActions,
    modifier: Modifier = Modifier,
) {
    val view = state.view
    val games = koinInject<GameRegistry>()
    val brands = koinInject<GamePlayRegistry>()
    val model = remember(state, now) {
        duelLegsModel(
            view = view,
            elapsedMs = state.elapsedMs,
            now = now,
            nameOf = games::displayName,
            rankOrderOf = games::rankOrderOf,
            myName = state.myName,
            firstSolvedAt = state.firstSolvedAt,
            dayOf = time::day,
        )
    }
    val content = LegsContent(model, view.match.deadline, time.ends(view.match.deadline), view.match.closesAt, view.match.code, now, actions)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = layout == PlayLayout.TwoPane
        when {
            model.self -> SelfDuelLegs(content, layout, brands, Modifier.fillMaxSize())
            twoPane && maxWidth >= TwoColumnMinWidth -> TwoColumnLegs(content, brands, maxHeight)
            else -> SingleColumnLegs(content, brands, capped = twoPane)
        }
    }
}

/** The phone's column; in a tablet pane too narrow for two columns ([capped]) it keeps to [maxContentWidth], centred, as the friend page does. */
@Composable
private fun SingleColumnLegs(content: LegsContent, brands: GamePlayRegistry, capped: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier = (if (capped) Modifier.maxContentWidth() else Modifier.fillMaxWidth()).padding(Spacing.lg).testTag("duel.legs.column"),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            LegsHeader(content)
            PuzzleList(content, brands, selected = null, onSelect = null)
            LegsFooter(content)
        }
    }
}

/** Everything the panes draw, so the pane composables take one value. */
internal class LegsContent(
    val model: DuelLegsModel,
    val deadline: Instant,
    val ends: String,
    val closesAt: Instant,
    val code: ShareCode,
    val now: Instant,
    val actions: LegsActions,
)

/**
 * The tablet pane's two columns on a pane [paneHeight] high ([TwoColumnFrame], as the friend page): the header, list
 * and footer on the left, and the selected puzzle's [LegDetail] in the fixed right column. The page is capped at
 * [MaxContentWidth] and centred; each column is centred in the pane when shorter than it, and the page scrolls from the top when taller.
 */
@Composable
private fun TwoColumnLegs(content: LegsContent, brands: GamePlayRegistry, paneHeight: Dp) {
    val rows = content.model.rows
    var picked by rememberSaveable { mutableIntStateOf(-1) }
    val selected = rows.firstOrNull { it.legIndex == picked } ?: rows.firstOrNull { it.action != null } ?: rows.firstOrNull()
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TwoColumnFrame(
            paneHeight = paneHeight,
            tag = DUEL_GRID_TAG,
            contentMaxWidth = MaxContentWidth,
            left = { column, _ ->
                Column(column, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    LegsHeader(content)
                    PuzzleList(content, brands, selected = selected?.legIndex, onSelect = { picked = it })
                    LegsFooter(content)
                }
            },
            right = {
                if (selected != null) {
                    LegDetail(selected, content.model.opponent.name, brands, onAction = { content.actions.onOpenLeg(selected) })
                }
            },
        )
    }
}

/** Marks the two-column page: `duel.legs.grid`, with `.left` and `.right` for its columns; its loading skeleton adds `.skeleton`. */
internal const val DUEL_GRID_TAG = "duel.legs.grid"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LegsHeader(content: LegsContent) {
    val model = content.model
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box {
                PlayerAvatar(model.me.name, model.me.photoUrl, size = AvatarSize)
                if (model.self) {
                    PastYouAvatar(model.me.name, Modifier.padding(start = AvatarOverlap), AvatarSize)
                } else {
                    PlayerAvatar(
                        name = model.opponent.name,
                        photoUrl = model.opponent.photoUrl,
                        modifier = Modifier.padding(start = AvatarOverlap),
                        size = AvatarSize,
                        tone = DuelTone.Them,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${model.me.name} vs ${model.opponent.name}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("duel.legs.title"),
                )
                Text(
                    text = if (model.self) "Best of ${model.rows.size} · against your first solves" else "Best of ${model.rows.size} · ends ${content.ends}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Once the reveal is ready the footer says why ("Both finished" / "Closed"); a countdown and "hidden" would be stale.
        if (model.footer != DuelFooter.RevealReady) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (model.self) {
                    Pill("Times hidden until the results")
                } else {
                    Pill(timeLeftText(content.deadline, content.now), Modifier.testTag("duel.legs.timeLeft"))
                    Pill("Score hidden until it settles")
                }
            }
        }
    }
}

@Composable
internal fun PuzzleList(content: LegsContent, brands: GamePlayRegistry, selected: Int?, onSelect: ((Int) -> Unit)?) {
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
                onClick = { content.actions.onNudge(nudgeText(name, content.code, content.deadline, content.now)) },
                modifier = Modifier.testTag("duel.legs.nudge"),
            )
        }
        DuelFooter.RevealReady -> FooterCard(if (isClosed(content.closesAt, content.now)) "Closed" else "Both finished") {
            PrimaryButton("See results", onClick = content.actions.onReveal, modifier = Modifier.testTag("duel.legs.reveal"))
        }
    }
}

@Composable
internal fun FooterCard(title: String, action: @Composable () -> Unit) {
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
