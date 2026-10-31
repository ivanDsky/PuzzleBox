package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Playful voice (ticket 10, promoted from prototype T3): Baloo 2 everywhere — chunky
// bold display and titles, sturdy medium text. One bundled variable TTF (OFL, license
// in core/designsystem/licenses/) registered once per weight with matching variation
// settings; Compose then picks the right entry exactly like a static family.

private fun baloo(weight: FontWeight) = Font(
    resId = R.font.baloo_2,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Baloo2 = FontFamily(
    baloo(FontWeight.Normal),
    baloo(FontWeight.Medium),
    baloo(FontWeight.SemiBold),
    baloo(FontWeight.Bold),
)

private fun playful(weight: FontWeight, size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = Baloo2,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val AppTypography = Typography(
    displayLarge = playful(FontWeight.Bold, 57, 64, -0.25),
    displayMedium = playful(FontWeight.Bold, 45, 52, -0.25),
    displaySmall = playful(FontWeight.Bold, 36, 44, 0.0),
    headlineLarge = playful(FontWeight.Bold, 32, 40, 0.0),
    headlineMedium = playful(FontWeight.Bold, 28, 36, 0.0),
    headlineSmall = playful(FontWeight.Bold, 24, 32, 0.0),
    titleLarge = playful(FontWeight.Bold, 22, 28, 0.0),
    titleMedium = playful(FontWeight.SemiBold, 16, 24, 0.15),
    titleSmall = playful(FontWeight.SemiBold, 14, 20, 0.1),
    bodyLarge = playful(FontWeight.Medium, 16, 24, 0.25),
    bodyMedium = playful(FontWeight.Medium, 14, 20, 0.25),
    bodySmall = playful(FontWeight.Medium, 12, 16, 0.4),
    labelLarge = playful(FontWeight.SemiBold, 14, 20, 0.1),
    labelMedium = playful(FontWeight.SemiBold, 12, 16, 0.5),
    labelSmall = playful(FontWeight.SemiBold, 11, 16, 0.5),
)
