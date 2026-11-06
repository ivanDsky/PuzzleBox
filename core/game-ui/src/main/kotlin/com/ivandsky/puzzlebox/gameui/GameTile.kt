package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayStatus

private val TileHeight = 156.dp
private val NameHeight = 22.dp
private val StatusHeight = 40.dp
private val MetaHeight = 16.dp
private val MarkSize = 14.dp

// The mark centres on the status text's first line, not on the two-line slot.
private val MarkCell = 20.dp
private val MarkGap = 6.dp
private val SlotGap = 2.dp

/**
 * One game's tile in a two-column grid (hub card handoff §Tile): the brand card with the
 * motif cropped top-end and name / status (two-line clamp) / meta pinned to the bottom.
 * An offline tile is neutral but still clickable: its tap is the retry. Loading (null
 * status) draws skeletons in the same slots.
 */
@Composable
fun GameTile(model: GameCardModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = model.status
    val colors = gameCardColors(model.brand, offline = status == TodayStatus.Offline)
    val id = model.game.value
    GameCardSurface(
        colors = colors,
        shape = AppShapes.large,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(TileHeight).testTag("card.tile.$id"),
        motif = {
            GameMotif(
                model.brand, 132.dp, 1.6f, Alignment.TopEnd, DpOffset(30.dp, (-26).dp),
                colors.motif, colors.motifAlpha,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Spacing.lg, top = Spacing.md, end = Spacing.md, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SlotGap),
        ) {
            CardSlot(model.name, NameHeight) { name ->
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CardSlot(status, StatusHeight, Modifier.testTag("card.status.$id")) { shown ->
                Row(Modifier.fillMaxWidth().height(StatusHeight)) {
                    Row(Modifier.height(MarkCell), verticalAlignment = Alignment.CenterVertically) {
                        StatusMark(shown, MarkSize, colors.fg)
                    }
                    Spacer(Modifier.width(MarkGap))
                    if (shown == null) {
                        Row(Modifier.height(MarkCell), verticalAlignment = Alignment.CenterVertically) {
                            SkeletonLine(0.7f, 12.dp, colors.fg.copy(alpha = SKELETON_ALPHA))
                        }
                    } else {
                        Text(
                            todayStatusLine(shown),
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val meta = if (status == null) null else cardMeta(model)
            CardSlot((status == null) to meta, MetaHeight, Modifier.testTag("card.meta.$id")) { (loading, text) ->
                if (loading) {
                    SkeletonLine(0.5f, 10.dp, colors.fg.copy(alpha = SKELETON_ALPHA))
                } else {
                    Text(
                        text.orEmpty(),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = colors.fg2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
