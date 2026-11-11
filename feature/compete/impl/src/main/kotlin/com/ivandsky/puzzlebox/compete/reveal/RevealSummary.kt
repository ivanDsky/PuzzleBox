package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry

private val PhoneMaxWidth = 560.dp
private val PanesMaxWidth = 1040.dp
private val CardPaneWidth = 400.dp

/**
 * The reveal's end: the final-score card (verdict, tiles, Rematch, Share / Watch again) and the puzzles list.
 * [wide] puts the card on the left and the list on the right, the two panes centred as a block, vertically too when
 * they fit and scrolling from the top when they don't; a phone has one column. Back arrow leaves through [onBack].
 */
@Composable
internal fun RevealSummary(
    summary: DuelSummary,
    brands: GamePlayRegistry,
    wide: Boolean,
    onBack: () -> Unit,
    onRematch: () -> Unit,
    onWatchAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("reveal.summary")) {
        RevealTopBar(summary.me, summary.opponent, summary.rows.size, onBack)
        if (wide) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val viewport = maxHeight
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        modifier = Modifier.widthIn(max = PanesMaxWidth).fillMaxWidth().padding(Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                    ) {
                        SummaryCard(summary, onRematch, onWatchAgain, Modifier.width(CardPaneWidth))
                        SummaryPuzzleList(summary, brands, Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    modifier = Modifier.widthIn(max = PhoneMaxWidth).fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    SummaryCard(summary, onRematch, onWatchAgain)
                    SummaryPuzzleList(summary, brands)
                }
            }
        }
    }
}
