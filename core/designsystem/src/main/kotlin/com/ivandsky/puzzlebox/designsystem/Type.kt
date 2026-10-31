package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Editorial voice (A12): serif display and headline styles, M3 defaults elsewhere.
// Large serif sizes carry slight negative tracking; small sizes keep the default.
private val Serif = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)

val AppTypography = Typography(
    displayLarge = Serif.copy(fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = Serif.copy(fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = Serif.copy(fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = Serif.copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = Serif.copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = Serif.copy(fontSize = 24.sp, lineHeight = 32.sp),
)
