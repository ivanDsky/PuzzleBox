package com.ivandsky.puzzlebox.train

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.train.tab.TechniqueList
import com.ivandsky.puzzlebox.train.tab.previewGames
import com.ivandsky.puzzlebox.train.tab.previewTechniques

/** The technique list on the preview games, with "Line lock" open when [open]. */
@Composable
internal fun GoldenTechniqueList(open: Boolean) = TechniqueList(
    games = previewGames,
    selected = previewGames.first().id,
    techniques = previewTechniques,
    openId = TechniqueId("line-lock").takeIf { open },
    onSelectGame = {},
    onOpen = {},
    modifier = Modifier.fillMaxSize(),
)
