package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.rankRuleText

private val MinTouch = 48.dp
private val StepSize = 24.dp

private val Steps = listOf(
    "The timer counts down to past you's time.",
    "Hints count against you.",
    "Past you's time stays hidden until the results.",
)

/**
 * A self duel's puzzles: the header and cards scroll, the footer (results, then Remove duel) stays at the bottom. On a
 * wide screen the left pane is that column and the right pane is the selected puzzle with its Start button pinned.
 */
@Composable
internal fun SelfDuelLegs(content: LegsContent, layout: PlayLayout, brands: GamePlayRegistry, modifier: Modifier) {
    if (layout != PlayLayout.TwoPane) {
        Column(modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                LegsHeader(content)
                PuzzleList(content, brands, selected = null, onSelect = null)
            }
            SelfFooter(content)
        }
        return
    }
    val rows = content.model.rows
    var picked by rememberSaveable { mutableIntStateOf(-1) }
    val selected = rows.firstOrNull { it.legIndex == picked } ?: rows.firstOrNull { it.action != null } ?: rows.firstOrNull()
    Row(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                LegsHeader(content)
                PuzzleList(content, brands, selected = selected?.legIndex, onSelect = { picked = it })
            }
            SelfFooter(content)
        }
        VerticalDivider()
        Column(Modifier.weight(1f).fillMaxSize()) {
            if (selected != null) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    SelfLegDetail(selected, content.model.opponent.name, brands)
                }
                LegActionButton(
                    row = selected,
                    onClick = { content.actions.onOpenLeg(selected) },
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun SelfLegDetail(row: LegRowModel, opponentName: String, brands: GamePlayRegistry) {
    Column(Modifier.testTag("duel.legs.detail"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GameGlyph(brands.brandOf(row.gameId), GameGlyphSize.Hero)
        Text(row.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(rankRuleText(row.rankOrder), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LegStatusPill(row.status)
        OpponentText(row.opponent, opponentName)
        PostingNote(row.posting)
        Callout(done = row.action == null)
    }
}

@Composable
private fun Callout(done: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(Spacing.md))
            .padding(Spacing.md)
            .testTag("duel.legs.callout"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = if (done) "DONE" else "IN THIS PUZZLE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (done) {
            Text(
                text = "You played this one. Past you's time shows in the results.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Steps.forEachIndexed { index, step -> CalloutStep(index + 1, step) }
        }
    }
}

@Composable
private fun CalloutStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(
            modifier = Modifier.size(StepSize).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The results card once there is something to see, then Remove duel; both stay at the bottom of the pane. */
@Composable
private fun SelfFooter(content: LegsContent) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (content.model.footer == DuelFooter.RevealReady) {
            FooterCard("You played all ${content.model.rows.size}") {
                PrimaryButton("See results", onClick = content.actions.onReveal, modifier = Modifier.testTag("duel.legs.reveal"))
            }
        }
        TextButton(
            onClick = content.actions.onRemove,
            modifier = Modifier.heightIn(min = MinTouch).testTag("duel.legs.remove"),
        ) {
            Text("Remove duel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
        }
    }
}
