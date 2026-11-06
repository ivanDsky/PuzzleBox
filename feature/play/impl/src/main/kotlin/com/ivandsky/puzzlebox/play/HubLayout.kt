package com.ivandsky.puzzlebox.play

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.NoNextKind
import com.ivandsky.puzzlebox.gameui.NoNextPanel
import com.ivandsky.puzzlebox.gameui.UpNextCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * Where a section draws one game's [card]: [modifier] places it in the section, and the
 * slot stays the same layout node wherever the game goes, so it can slide between sections.
 */
internal typealias HubSlot = @Composable (game: GameId, modifier: Modifier, card: @Composable () -> Unit) -> Unit

/** A game's movable slot: its placement, its screen-order index, reduced motion, its card. */
private typealias MovableSlot = @Composable (Modifier, Int, Boolean, @Composable () -> Unit) -> Unit

/** Two-pane widths of the hero and the Also-today column (hub-card §Layout). */
private const val HERO_WEIGHT = 5f
private const val SIDE_WEIGHT = 6f

private val SectionLabelHeight = 24.dp
private val EmptyLineHeight = 44.dp

private val SlotMove = BoundsTransform { _, _ ->
    spring(
        dampingRatio = MotionTokens.SpringDampingDefault,
        stiffness = MotionTokens.SpringStiffnessDefault,
        visibilityThreshold = Rect.VisibilityThreshold,
    )
}

/**
 * The hub's sections from [plan] (hub-card §Layout): the Up-next card, or the no-next panel
 * in its place, then [HubAlsoToday]'s tiles and [HubDoneToday]'s chips, drawn from [rows].
 * A phone stacks them; [twoPane] puts the hero and Also today side by side (5:6) over Done
 * today at full width. Every game's card lives in its own movable slot inside one
 * [LookaheadScope], so a game that changes section slides to its new place on the default
 * spring rather than jumping (spec A8; reduced motion: it jumps). Cards fade in on first
 * show, staggered in screen order, with no rise (hub-card §Motion). Tapping a card is [onPick].
 */
@Composable
internal fun HubLayout(
    plan: HubPlan,
    rows: List<HubRow>,
    twoPane: Boolean,
    onPick: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val models = remember(rows) { rows.associate { it.game to it.toCardModel() } }
    val reduced = rememberReducedMotion()
    val hero = plan.hero
    val order = listOfNotNull((hero as? HubHero.Next)?.game) + plan.alsoToday + plan.doneToday
    val alsoToday = plan.alsoToday.mapNotNull(models::get)
    val doneToday = plan.doneToday.mapNotNull(models::get)
    LookaheadScope {
        val scope = this
        val slots = remember { HashMap<GameId, MovableSlot>() }
        val slot: HubSlot = { game, slotModifier, card ->
            val content = slots.getOrPut(game) { movableSlot(scope) }
            content(slotModifier.testTag("hub.row.${game.value}"), order.indexOf(game), reduced, card)
        }
        val heroSlot: @Composable (Modifier) -> Unit = { heroModifier ->
            val panel = hero.noNextKind()
            if (panel != null) {
                EnterFade(index = 0, reduced = reduced, modifier = heroModifier) {
                    NoNextPanel(kind = panel, done = doneToday.map(GameCardModel::brand), tall = twoPane)
                }
            } else if (hero is HubHero.Next) {
                models[hero.game]?.let { model ->
                    slot(hero.game, heroModifier) { UpNextCard(model, tall = twoPane, onClick = { onPick(hero.game) }) }
                }
            }
        }
        val stillToPlay = hero !is HubHero.Next
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (twoPane) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.Top) {
                    heroSlot(Modifier.weight(HERO_WEIGHT))
                    HubAlsoToday(alsoToday, stillToPlay, slot, onPick, Modifier.weight(SIDE_WEIGHT))
                }
            } else {
                heroSlot(Modifier.fillMaxWidth())
                HubAlsoToday(alsoToday, stillToPlay, slot, onPick)
            }
            HubDoneToday(doneToday, slot, onPick)
        }
    }
}

/** A section's Eyebrow label, its text on the bottom of a fixed-height line. */
@Composable
internal fun HubSectionLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier.height(SectionLabelHeight), contentAlignment = Alignment.BottomStart) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The one line an empty section keeps, so the screen never collapses around it. */
@Composable
internal fun HubEmptyLine(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(EmptyLineHeight), contentAlignment = Alignment.CenterStart) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun movableSlot(scope: LookaheadScope): MovableSlot =
    movableContentOf { modifier: Modifier, index: Int, reduced: Boolean, card: @Composable () -> Unit ->
        val move = if (reduced) Modifier else Modifier.animateBounds(scope, boundsTransform = SlotMove)
        EnterFade(index, reduced, modifier.then(move)) { card() }
    }

/**
 * [content] fading in once, on first show: the Enter job without its rise, delayed by
 * [index] stagger steps up to the cap; reduced motion fades plainly with no stagger.
 * Previews show it settled.
 */
@Composable
private fun EnterFade(index: Int, reduced: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val settled = LocalInspectionMode.current
    val alpha = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(alpha) {
        val spec = if (reduced) {
            tween<Float>(MotionTokens.ReducedFadeDuration)
        } else {
            tween(
                durationMillis = MotionTokens.EnterDuration,
                delayMillis = index.coerceIn(0, MotionTokens.EnterStaggerCap) * MotionTokens.EnterStagger,
                easing = MotionTokens.EasingEmphasizedDecelerate,
            )
        }
        alpha.animateTo(1f, spec)
    }
    Box(modifier.graphicsLayer { this.alpha = alpha.value }, content = content)
}

/** Why the panel stands in the hero's place; null when a game is next. */
private fun HubHero.noNextKind(): NoNextKind? = when (this) {
    is HubHero.Next -> null
    HubHero.Loading -> NoNextKind.Loading
    HubHero.AllSolved -> NoNextKind.AllSolved
    HubHero.RestOffline -> NoNextKind.RestOffline
}
