package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The ten recap cards, in story order. Each has one fixed field, an ink that sits on it and a plate, the same
// everywhere the card shows (story card, tablet fact-row marker, share poster).
enum class RecapCard { Cover, Solved, Fastest, Streak, Moments, Friends, TotalTime, YearMonth, YearFinale, Share }

data class RecapCardColors(val field: Color, val ink: Color, val plate: Color)

// Palette v2 (contract-shared.md `## Recap palette`): three hue families plus a mist neutral. Secondary text is the
// ink at full opacity, never alpha; a control inside a plate gets a 1.5dp ink outline.
internal val LightRecapCoverField = Color(0xFF92F0AD)
internal val LightOnCover = Color(0xFF193521)
internal val LightCoverPlate = Color(0xFFC7FFD4)
internal val LightRecapSolvedField = Color(0xFFFFE38D)
internal val LightOnSolved = Color(0xFF372D0C)
internal val LightSolvedPlate = Color(0xFFFFF5D7)
internal val LightRecapFastestField = Color(0xFFF2A7E7)
internal val LightOnFastest = Color(0xFF3C2439)
internal val LightFastestPlate = Color(0xFFFDC7F4)
internal val LightRecapStreakField = Color(0xFFEBC751)
internal val LightOnStreak = Color(0xFF372D0C)
internal val LightStreakPlate = Color(0xFFF9E095)
internal val LightRecapMomentsField = Color(0xFFFDD7F7)
internal val LightOnMoments = Color(0xFF362934)
internal val LightMomentsPlate = Color(0xFFFFF0FC)
internal val LightRecapFriendsField = Color(0xFFC3F3CE)
internal val LightOnFriends = Color(0xFF223226)
internal val LightFriendsPlate = Color(0xFFDFFFE6)
internal val LightRecapTotalTimeField = Color(0xFF75CE8F)
internal val LightOnTotalTime = Color(0xFF183420)
internal val LightTotalTimePlate = Color(0xFFA3DFB2)
internal val LightRecapYearMonthField = Color(0xFFC6D2E2)
internal val LightOnYearMonth = Color(0xFF2A2E34)
internal val LightYearMonthPlate = Color(0xFFE0E9F4)
internal val LightRecapYearFinaleField = Color(0xFFF9D460)
internal val LightOnYearFinale = Color(0xFF372D0C)
internal val LightYearFinalePlate = Color(0xFFFFEEBB)
internal val LightRecapShareField = Color(0xFFEDF2FA)
internal val LightOnShare = Color(0xFF2C2E31)
internal val LightSharePlate = Color(0xFFF3F7FD)

internal val DarkRecapCoverField = Color(0xFF86DB9D)
internal val DarkOnCover = Color(0xFF193521)
internal val DarkCoverPlate = Color(0xFFB1EBC0)
internal val DarkRecapSolvedField = Color(0xFFEBCF7A)
internal val DarkOnSolved = Color(0xFF372D0D)
internal val DarkSolvedPlate = Color(0xFFFAE7AD)
internal val DarkRecapFastestField = Color(0xFFDA97D0)
internal val DarkOnFastest = Color(0xFF321A2F)
internal val DarkFastestPlate = Color(0xFFE6B5DE)
internal val DarkRecapStreakField = Color(0xFFD5B449)
internal val DarkOnStreak = Color(0xFF342A09)
internal val DarkStreakPlate = Color(0xFFE3CC88)
internal val DarkRecapMomentsField = Color(0xFFE9C3E3)
internal val DarkOnMoments = Color(0xFF362934)
internal val DarkMomentsPlate = Color(0xFFFADEF5)
internal val DarkRecapFriendsField = Color(0xFFAFDFBB)
internal val DarkOnFriends = Color(0xFF223226)
internal val DarkFriendsPlate = Color(0xFFD1F2D8)
internal val DarkRecapTotalTimeField = Color(0xFF69B980)
internal val DarkOnTotalTime = Color(0xFF072311)
internal val DarkTotalTimePlate = Color(0xFF94CAA1)
internal val DarkRecapYearMonthField = Color(0xFFADB9C8)
internal val DarkOnYearMonth = Color(0xFF282C32)
internal val DarkYearMonthPlate = Color(0xFFC6CFD9)
internal val DarkRecapYearFinaleField = Color(0xFFE2C157)
internal val DarkOnYearFinale = Color(0xFF372D0C)
internal val DarkYearFinalePlate = Color(0xFFF0DA94)
internal val DarkRecapShareField = Color(0xFFD8DFE8)
internal val DarkOnShare = Color(0xFF2C2E31)
internal val DarkSharePlate = Color(0xFFF0F6FC)

internal val LightRecapCards: Map<RecapCard, RecapCardColors> = mapOf(
    RecapCard.Cover to RecapCardColors(LightRecapCoverField, LightOnCover, LightCoverPlate),
    RecapCard.Solved to RecapCardColors(LightRecapSolvedField, LightOnSolved, LightSolvedPlate),
    RecapCard.Fastest to RecapCardColors(LightRecapFastestField, LightOnFastest, LightFastestPlate),
    RecapCard.Streak to RecapCardColors(LightRecapStreakField, LightOnStreak, LightStreakPlate),
    RecapCard.Moments to RecapCardColors(LightRecapMomentsField, LightOnMoments, LightMomentsPlate),
    RecapCard.Friends to RecapCardColors(LightRecapFriendsField, LightOnFriends, LightFriendsPlate),
    RecapCard.TotalTime to RecapCardColors(LightRecapTotalTimeField, LightOnTotalTime, LightTotalTimePlate),
    RecapCard.YearMonth to RecapCardColors(LightRecapYearMonthField, LightOnYearMonth, LightYearMonthPlate),
    RecapCard.YearFinale to RecapCardColors(LightRecapYearFinaleField, LightOnYearFinale, LightYearFinalePlate),
    RecapCard.Share to RecapCardColors(LightRecapShareField, LightOnShare, LightSharePlate),
)

internal val DarkRecapCards: Map<RecapCard, RecapCardColors> = mapOf(
    RecapCard.Cover to RecapCardColors(DarkRecapCoverField, DarkOnCover, DarkCoverPlate),
    RecapCard.Solved to RecapCardColors(DarkRecapSolvedField, DarkOnSolved, DarkSolvedPlate),
    RecapCard.Fastest to RecapCardColors(DarkRecapFastestField, DarkOnFastest, DarkFastestPlate),
    RecapCard.Streak to RecapCardColors(DarkRecapStreakField, DarkOnStreak, DarkStreakPlate),
    RecapCard.Moments to RecapCardColors(DarkRecapMomentsField, DarkOnMoments, DarkMomentsPlate),
    RecapCard.Friends to RecapCardColors(DarkRecapFriendsField, DarkOnFriends, DarkFriendsPlate),
    RecapCard.TotalTime to RecapCardColors(DarkRecapTotalTimeField, DarkOnTotalTime, DarkTotalTimePlate),
    RecapCard.YearMonth to RecapCardColors(DarkRecapYearMonthField, DarkOnYearMonth, DarkYearMonthPlate),
    RecapCard.YearFinale to RecapCardColors(DarkRecapYearFinaleField, DarkOnYearFinale, DarkYearFinalePlate),
    RecapCard.Share to RecapCardColors(DarkRecapShareField, DarkOnShare, DarkSharePlate),
)

/** A card's colours for the light or dark theme. */
fun recapCardColors(card: RecapCard, darkTheme: Boolean): RecapCardColors =
    (if (darkTheme) DarkRecapCards else LightRecapCards).getValue(card)

/** This card's field in the resolved theme ([LocalDarkTheme]), so a forced appearance still flips it. */
val RecapCard.field: Color
    @Composable
    @ReadOnlyComposable
    get() = recapCardColors(this, LocalDarkTheme.current).field

/** The ink that sits on this card's field and plate: 7:1 on the field, 4.5:1 on the plate. */
val RecapCard.ink: Color
    @Composable
    @ReadOnlyComposable
    get() = recapCardColors(this, LocalDarkTheme.current).ink

/** This card's plate, the lifted surface a band, chip or control sits on. */
val RecapCard.plate: Color
    @Composable
    @ReadOnlyComposable
    get() = recapCardColors(this, LocalDarkTheme.current).plate

// Preview — Studio tooling only. Every card's field, ink and plate with a plate control outlined in ink.
@PuzzleBoxPreviews
@Composable
internal fun RecapPalettePreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.md)) {
            RecapCard.entries.forEach { card ->
                Row(Modifier.background(card.field).padding(Spacing.sm)) {
                    Text(card.name, color = card.ink)
                    Box(
                        Modifier
                            .padding(start = Spacing.sm)
                            .size(48.dp)
                            .background(card.plate)
                            .border(1.5.dp, card.ink),
                    )
                }
            }
        }
    }
}
