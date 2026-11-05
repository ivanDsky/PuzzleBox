package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner scale as tokens so components never invent radii (spec A5, identity §2.4):
// badge 6 · tile 12 (glyph tiles, icon wells) · medium 16 · card 20 · sheet 28.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The control shape: every button is a pill. */
val PillShape = RoundedCornerShape(percent = 50)
