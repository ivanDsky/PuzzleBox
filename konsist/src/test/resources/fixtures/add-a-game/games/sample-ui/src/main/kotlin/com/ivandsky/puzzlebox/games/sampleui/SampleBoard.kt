package com.ivandsky.puzzlebox.games.sampleui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SampleBoard(label: String, modifier: Modifier = Modifier) {
    Text(text = label, modifier = modifier)
}
