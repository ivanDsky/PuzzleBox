package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.gameui.GameBrand

private val DiscSize = 28.dp
private val GlyphSize = 16.dp

/**
 * One shelf: a head of [title] and its [sub] line, with the game's [disc] before them when it is a game shelf, over a
 * row of cards that [content] lists. The row scrolls sideways and starts level with the page's edge. With [onSeeAll]
 * the head ends in a See all link (tagged [seeAllTag]) that opens the shelf's own page.
 */
@Composable
internal fun ShelfRow(
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    disc: GameBrand? = null,
    onSeeAll: (() -> Unit)? = null,
    seeAllTag: String = "shelves.seeall",
    content: LazyListScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.lg, end = if (onSeeAll != null) Spacing.xs else Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (disc != null) GameDisc(disc)
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sub,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll, modifier = Modifier.heightIn(min = 48.dp).testTag(seeAllTag)) { Text("See all") }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

/** A game's glyph in its accent on a small disc: the lead of its shelf's head. */
@Composable
internal fun GameDisc(brand: GameBrand, modifier: Modifier = Modifier) {
    val colors = brand.accent.colors()
    Box(modifier.size(DiscSize).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
        Icon(brand.glyph, contentDescription = null, modifier = Modifier.size(GlyphSize), tint = colors.onAccent)
    }
}
