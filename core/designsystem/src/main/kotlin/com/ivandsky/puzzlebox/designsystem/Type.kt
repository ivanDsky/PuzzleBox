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
    baloo(FontWeight.ExtraBold),
)

private fun playful(weight: FontWeight, size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = Baloo2,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

// The nine type roles (spec A4, identity §2.3). Eight ride M3 slots below; Wordmark is
// the custom ninth. One Headline per screen; section heads are Title.
private val Display = playful(FontWeight.Bold, 40, 44, -0.5)
private val Headline = playful(FontWeight.Bold, 28, 34, -0.25)
private val Title = playful(FontWeight.Bold, 20, 26, 0.0)
private val TitleSmall = playful(FontWeight.SemiBold, 16, 22, 0.0)
private val Body = playful(FontWeight.Medium, 16, 24, 0.1)
private val BodySmall = playful(FontWeight.Medium, 14, 20, 0.1)
private val Label = playful(FontWeight.SemiBold, 13, 16, 0.2)

/** Kicker role ("TODAY", the date): callers pass upper-cased text — a TextStyle can't. */
private val Eyebrow = playful(FontWeight.Bold, 12, 16, 1.2)

/** The Wordmark role: the "PuzzleBox" name in the top bar, nowhere else. */
val WordmarkTextStyle: TextStyle = playful(FontWeight.ExtraBold, 24, 28, -0.5)

/** Primary button label (identity §2.4): Label at 16/20 weight 700. */
internal val PrimaryButtonTextStyle: TextStyle = Label.copy(
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 20.sp,
)

/** GameButton label (prototype 07 C): Label at 12/16 weight 800. */
internal val GameButtonTextStyle: TextStyle = Label.copy(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)

/**
 * Tabular digits (Baloo 2 `tnum`, spec A4): every time from [formatDuration] renders
 * through this so a ticking clock doesn't jitter as digit widths change.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

// Each role's own slot first; the unused slots alias to the nearest role.
val AppTypography = Typography(
    displayLarge = Display,
    displayMedium = Display,
    displaySmall = Display,
    headlineLarge = Headline,
    headlineMedium = Headline,
    headlineSmall = Title,
    titleLarge = Title,
    titleMedium = TitleSmall,
    titleSmall = TitleSmall,
    bodyLarge = Body,
    bodyMedium = BodySmall,
    bodySmall = BodySmall,
    labelLarge = Label,
    // Nav-bar labels: M3's NavigationBar reads labelMedium, and the nav runs Label at 12/16.
    labelMedium = Label.copy(fontSize = 12.sp),
    labelSmall = Eyebrow,
)
