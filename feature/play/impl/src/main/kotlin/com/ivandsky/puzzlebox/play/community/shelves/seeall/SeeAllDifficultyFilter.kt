package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.SingleFilterSheet
import com.ivandsky.puzzlebox.designsystem.community.TextDropdownFilter
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.resultui.levelFilterOption

private val DifficultyOptions = DifficultyChoice.entries.map {
    levelFilterOption(it, it.label, it.mark, tag = "seeall.filter.difficulty.${it.name.lowercase()}")
}

/** "Any difficulty ▾", or the picked one over an underline; a tap opens the difficulty sheet. */
@Composable
internal fun SeeAllDifficultyFilter(difficulty: DifficultyChoice?, onSelect: (DifficultyChoice?) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextDropdownFilter(
        label = "Any difficulty",
        value = difficulty?.label,
        open = open,
        onClick = { open = true },
        modifier = modifier.testTag("seeall.filter.difficulty"),
    )
    if (open) {
        SingleFilterSheet(
            title = "Difficulty",
            allLabel = "Any difficulty",
            options = DifficultyOptions,
            selected = difficulty,
            onSelect = onSelect,
            onDismiss = { open = false },
            tag = "seeall.filter.difficulty.sheet",
            allTag = "seeall.filter.difficulty.any",
        )
    }
}
