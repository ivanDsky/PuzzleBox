package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Ticket 08 typography prototypes. The size scale matches the editorial table exactly
// (Type.kt) so the switcher compares VOICE — family, weight, tracking — never size.
// Fonts are bundled variable TTFs (OFL, licenses in core/designsystem/licenses/);
// one file per family covers every weight via variation settings.

private val NunitoSansRes = R.font.nunito_sans
private val Baloo2Res = R.font.baloo_2

// One variable font file, registered once per weight with matching variation settings:
// Compose then picks the right entry exactly like a static family.
private fun variableStyle(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** T2 "Clean" — humanist sans voice (Nunito Sans), medium weights, tight display. */
private val CleanFamily = FontFamily(
    variableStyle(NunitoSansRes, FontWeight.Normal),
    variableStyle(NunitoSansRes, FontWeight.Medium),
    variableStyle(NunitoSansRes, FontWeight.SemiBold),
    variableStyle(NunitoSansRes, FontWeight.Bold),
)

/** T3 "Playful" — rounded chunky voice (Baloo 2), bold display, sturdy text. */
private val PlayfulFamily = FontFamily(
    variableStyle(Baloo2Res, FontWeight.Normal),
    variableStyle(Baloo2Res, FontWeight.Medium),
    variableStyle(Baloo2Res, FontWeight.SemiBold),
    variableStyle(Baloo2Res, FontWeight.Bold),
)

private fun clean(weight: FontWeight, size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = CleanFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private fun playful(weight: FontWeight, size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = PlayfulFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val CleanTypography = Typography(
    displayLarge = clean(FontWeight.SemiBold, 57, 64, -0.5),
    displayMedium = clean(FontWeight.SemiBold, 45, 52, -0.5),
    displaySmall = clean(FontWeight.SemiBold, 36, 44, -0.25),
    headlineLarge = clean(FontWeight.SemiBold, 32, 40, -0.25),
    headlineMedium = clean(FontWeight.SemiBold, 28, 36, -0.25),
    headlineSmall = clean(FontWeight.SemiBold, 24, 32, -0.25),
    titleLarge = clean(FontWeight.SemiBold, 22, 28, 0.0),
    titleMedium = clean(FontWeight.SemiBold, 16, 24, 0.15),
    titleSmall = clean(FontWeight.SemiBold, 14, 20, 0.1),
    bodyLarge = clean(FontWeight.Normal, 16, 24, 0.5),
    bodyMedium = clean(FontWeight.Normal, 14, 20, 0.25),
    bodySmall = clean(FontWeight.Normal, 12, 16, 0.4),
    labelLarge = clean(FontWeight.SemiBold, 14, 20, 0.1),
    labelMedium = clean(FontWeight.SemiBold, 12, 16, 0.5),
    labelSmall = clean(FontWeight.SemiBold, 11, 16, 0.5),
)

val PlayfulTypography = Typography(
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
