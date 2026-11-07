package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The widest a root list or form grows on a large window; past it the content centres. */
val MaxContentWidth: Dp = 720.dp

/** Fills the available width up to [MaxContentWidth], centred in whatever room is left. */
fun Modifier.maxContentWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = MaxContentWidth).fillMaxWidth()
