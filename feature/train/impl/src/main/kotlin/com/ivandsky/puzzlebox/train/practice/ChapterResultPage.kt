package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The pane is two columns from this wide; narrower it is one, with the actions moved up. */
private val TwoColumns = 720.dp
private val LeftColumn = 340.dp
private val ContentMax = 820.dp
private val TopBarHeight = 56.dp

/**
 * What the chapter came to (prototype 03, variant A): how it went in words, the actions, and the run's counts. It takes
 * the whole pane in place of the board. Wide it is two columns (the words and actions left, the counts and the board
 * by board rows right); narrow, one, in the order ring, words, actions, note, best, counts, rows, so the actions are in
 * view without scrolling. [onNext] opens the next technique's chapter, [onAgain] this one's from its first board,
 * [onBack] returns to the techniques (the pane's own close). With [showBack] a top bar with the technique's name and a
 * back arrow comes first: the phone, where this page covers the list.
 */
@Composable
internal fun ChapterResultPage(
    result: ChapterResult,
    showBack: Boolean,
    onNext: () -> Unit,
    onAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("train.result")) {
        if (showBack) {
            Row(Modifier.fillMaxWidth().height(TopBarHeight), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack)
                Text(result.technique.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= TwoColumns) {
                WideResult(result, onNext, onAgain, onBack)
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).rise().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                    ResultLeft(result, onNext, onAgain, onBack)
                    ResultRight(result, Modifier.padding(top = Spacing.md))
                }
            }
        }
    }
}

/** The two columns as one block, top-aligned to each other and centered in the pane both ways, so a short page leaves no gap below. */
@Composable
private fun WideResult(result: ChapterResult, onNext: () -> Unit, onAgain: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = ContentMax).rise().padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            ResultLeft(result, onNext, onAgain, onBack, Modifier.width(LeftColumn).verticalScroll(rememberScrollState()))
            ResultRight(result, Modifier.weight(1f).verticalScroll(rememberScrollState()))
        }
    }
}

/** The words, the actions, the last technique's note and the best run. */
@Composable
private fun ResultLeft(result: ChapterResult, onNext: () -> Unit, onAgain: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        ResultHero(result)
        ResultActions(result, onNext, onAgain, onBack)
        lastTechniqueNote(result)?.let { LastTechniqueNote(it) }
        BestRow(result.best)
    }
}

/** The run's counts and the board by board rows. */
@Composable
private fun ResultRight(result: ChapterResult, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        StatTiles(result)
        BoardRows(result.rows)
    }
}
