package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayStatus

/** Phone height includes the reserved line slot (hub-card ruling 1: 152 → 172). */
internal val UpNextPhoneHeight = 172.dp
internal val UpNextTabletHeight = 356.dp

private val ChevronSize = 28.dp
private val MarkGap = 6.dp
private const val MOTIF_STROKE = 1.4f
private const val STATUS_SKELETON_WIDTH = 0.55f

/**
 * The hub's Up-next hero for [model] (hub-card §Up-next card): the brand-filled card with
 * its motif cropped bottom-end and eyebrow, name, status, meta and line on fixed slots,
 * so the card's height never depends on the game. [tall] is the tablet layout: chevron
 * top-end, text pinned to the bottom. Never offline: the hub skips offline games for next.
 */
@Composable
fun UpNextCard(model: GameCardModel, tall: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = gameCardColors(model.brand, offline = false)
    GameCardSurface(
        colors = colors,
        shape = AppShapes.extraLarge,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(if (tall) UpNextTabletHeight else UpNextPhoneHeight)
            .testTag("card.next.${model.game.value}"),
        motif = {
            GameMotif(
                brand = model.brand,
                size = if (tall) 300.dp else 210.dp,
                stroke = MOTIF_STROKE,
                alignment = Alignment.BottomEnd,
                offset = if (tall) DpOffset(50.dp, 70.dp) else DpOffset(34.dp, 54.dp),
                tint = colors.motif,
                alpha = colors.motifAlpha,
            )
        },
    ) {
        if (tall) {
            Chevron(Modifier.align(Alignment.TopEnd).padding(Spacing.xl))
            UpNextText(model, colors, Modifier.align(Alignment.BottomStart).padding(Spacing.xl))
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(PaddingValues(start = Spacing.xl, top = Spacing.xl, end = Spacing.lg, bottom = Spacing.xl)),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UpNextText(model, colors, Modifier.weight(1f))
                Chevron()
            }
        }
    }
}

@Composable
private fun Chevron(modifier: Modifier = Modifier) {
    Icon(PuzzleIcons.Chevron, contentDescription = null, modifier = modifier.size(ChevronSize))
}

@Composable
private fun UpNextText(model: GameCardModel, colors: GameCardColors, modifier: Modifier = Modifier) {
    val id = model.game.value
    val type = MaterialTheme.typography
    Column(modifier.fillMaxWidth()) {
        CardSlot(value = Unit, height = 16.dp) {
            Text("UP NEXT", style = type.labelSmall, color = colors.fg2, maxLines = 1)
        }
        CardSlot(value = model.name, height = 34.dp) {
            Text(it, style = type.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CardSlot(value = model.status, height = 24.dp, modifier = Modifier.testTag("card.status.$id")) {
            StatusLine(it, colors.fg)
        }
        CardSlot(value = if (model.status == null) null else cardMeta(model), height = 20.dp, modifier = Modifier.testTag("card.meta.$id")) {
            Text(it.orEmpty(), style = type.labelLarge.tabular(), color = colors.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CardSlot(value = model.line, height = 20.dp, modifier = Modifier.testTag("card.line.$id")) {
            Text(it.orEmpty(), style = type.bodyMedium, color = colors.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusLine(status: TodayStatus?, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusMark(status = status, size = 16.dp, tint = tint)
        Spacer(Modifier.width(MarkGap))
        if (status == null) {
            SkeletonLine(STATUS_SKELETON_WIDTH, 12.dp, tint.copy(alpha = SKELETON_ALPHA))
        } else {
            Text(
                todayStatusLine(status),
                style = MaterialTheme.typography.bodyLarge.tabular(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
