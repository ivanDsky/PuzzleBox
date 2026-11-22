package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.community.ShelfCardDefaults
import com.ivandsky.puzzlebox.designsystem.skeleton

private val SpotlightHeight = 136.dp
private const val SHELVES = 2
private const val CARDS = 3
private val CardHeight = ShelfCardDefaults.ArtHeight + 44.dp

/**
 * The shelves home while it loads: a spotlight block and two shelves of cards in the places the real ones take, each
 * a [skeleton] that shimmers (still under reduced motion).
 */
@Composable
internal fun ShelvesSkeleton(modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier.testTag("shelves.skeleton").clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(
            Modifier.padding(horizontal = Spacing.lg).fillMaxWidth().height(SpotlightHeight)
                .skeleton(fill, RoundedCornerShape(ShelfCardDefaults.SpotlightCorner)),
        )
        repeat(SHELVES) {
            Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Spacer(Modifier.width(140.dp).height(18.dp).skeleton(fill, RoundedCornerShape(6.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    repeat(CARDS) {
                        Spacer(
                            Modifier.size(ShelfCardDefaults.CardWidth, CardHeight)
                                .skeleton(fill, RoundedCornerShape(ShelfCardDefaults.CardCorner)),
                        )
                    }
                }
            }
        }
    }
}
