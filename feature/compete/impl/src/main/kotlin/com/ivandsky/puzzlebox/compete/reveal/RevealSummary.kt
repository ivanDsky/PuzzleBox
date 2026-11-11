package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
private val StageMaxWidth = 1352.dp

/**
 * The reveal's end. [wide] is the Stage page: a hero card over the full-width puzzles table, centred as a block,
 * vertically too when it fits and scrolling from the top when it doesn't. A phone has one column: the score card, then
 * the puzzles. Back arrow leaves through [onBack].
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
                    StageSummary(
                        summary = summary,
                        brands = brands,
                        onRematch = onRematch,
                        onWatchAgain = onWatchAgain,
                        modifier = Modifier
                            .widthIn(max = StageMaxWidth)
                            .fillMaxWidth()
                            .padding(start = Spacing.xxl, top = Spacing.xs, end = Spacing.xxl, bottom = Spacing.xxl),
                    )
                }
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    modifier = Modifier.widthIn(max = PhoneMaxWidth).fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    SummaryCard(summary, onRematch, onWatchAgain)
                    SummaryPuzzleList(summary, brands, wide = false)
                }
            }
        }
    }
}
