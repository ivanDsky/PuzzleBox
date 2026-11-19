package com.ivandsky.puzzlebox.train.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The Games screen's Train tab body. It is empty for now: the technique list, the snippet round and the drill puzzle
 * land in later tickets of the Trainer epic, as state local to this pane (no nav key). The app hands this to Games
 * through a slot, and only while some game has a trainer, so the tab never opens on this empty body in a shipped build.
 */
@Composable
fun TrainTab(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().testTag("train-tab"))
}
