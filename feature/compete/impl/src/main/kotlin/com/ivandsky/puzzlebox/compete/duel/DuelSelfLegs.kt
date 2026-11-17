package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.maxContentWidth
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry

/**
 * A self duel's puzzles in one column, on a phone and in a tablet pane too narrow for two ([capped], kept to the content
 * width and centred): the header and cards scroll, the footer (results, then Remove duel) stays at the bottom.
 */
@Composable
internal fun SelfDuelLegs(content: LegsContent, brands: GamePlayRegistry, capped: Boolean) {
    Column((if (capped) Modifier.fillMaxHeight().maxContentWidth() else Modifier.fillMaxSize())) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            LegsHeader(content)
            PuzzleList(content, brands, selected = null, onSelect = null)
        }
        SelfFooter(content, Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg))
    }
}
