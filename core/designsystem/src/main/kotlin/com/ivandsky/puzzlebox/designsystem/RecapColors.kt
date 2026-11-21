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
import java.time.Month

// The ten recap cards, in story order. Each has one fixed field, an ink that sits on it and a plate, the same
// everywhere the card shows (story card, tablet fact-row marker, share poster).
enum class RecapCard { Cover, Solved, Fastest, Streak, Moments, Friends, TotalTime, YearMonth, YearFinale, Share }

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

// Month palette (prototype 14): one hue per calendar month, January's sky round the wheel to December's mint, so no
// two month cards share a field. Each ink reads 4.5:1 or more on its field and its plate in both themes.
internal val LightMonthCards: Map<Month, RecapCardColors> = mapOf(
    Month.JANUARY to RecapCardColors(Color(0xFF9AD6F4), Color(0xFF122A36), Color(0xFFD4EFFC)),
    Month.FEBRUARY to RecapCardColors(Color(0xFF9AA9F4), Color(0xFF121836), Color(0xFFD4DBFC)),
    Month.MARCH to RecapCardColors(Color(0xFFB89AF4), Color(0xFF1E1236), Color(0xFFE1D4FC)),
    Month.APRIL to RecapCardColors(Color(0xFFE59AF4), Color(0xFF301236), Color(0xFFF6D4FC)),
    Month.MAY to RecapCardColors(Color(0xFFF49AD6), Color(0xFF36122A), Color(0xFFFCD4EF)),
    Month.JUNE to RecapCardColors(Color(0xFFF49AA9), Color(0xFF361218), Color(0xFFFCD4DB)),
    Month.JULY to RecapCardColors(Color(0xFFF4B89A), Color(0xFF361E12), Color(0xFFFCE1D4)),
    Month.AUGUST to RecapCardColors(Color(0xFFF4E59A), Color(0xFF363012), Color(0xFFFCF6D4)),
    Month.SEPTEMBER to RecapCardColors(Color(0xFFD6F49A), Color(0xFF2A3612), Color(0xFFEFFCD4)),
    Month.OCTOBER to RecapCardColors(Color(0xFFA9F49A), Color(0xFF183612), Color(0xFFDBFCD4)),
    Month.NOVEMBER to RecapCardColors(Color(0xFF9AF4B8), Color(0xFF12361E), Color(0xFFD4FCE1)),
    Month.DECEMBER to RecapCardColors(Color(0xFF9AF4E5), Color(0xFF123630), Color(0xFFD4FCF6)),
)

internal val DarkMonthCards: Map<Month, RecapCardColors> = mapOf(
    Month.JANUARY to RecapCardColors(Color(0xFF83C2E2), Color(0xFF0F2733), Color(0xFFB7DDF0)),
    Month.FEBRUARY to RecapCardColors(Color(0xFF8393E2), Color(0xFF0F1533), Color(0xFFB7C1F0)),
    Month.MARCH to RecapCardColors(Color(0xFFA383E2), Color(0xFF1B0F33), Color(0xFFCAB7F0)),
    Month.APRIL to RecapCardColors(Color(0xFFD283E2), Color(0xFF2D0F33), Color(0xFFE6B7F0)),
    Month.MAY to RecapCardColors(Color(0xFFE283C2), Color(0xFF330F27), Color(0xFFF0B7DD)),
    Month.JUNE to RecapCardColors(Color(0xFFE28393), Color(0xFF330F15), Color(0xFFF0B7C1)),
    Month.JULY to RecapCardColors(Color(0xFFE2A383), Color(0xFF331B0F), Color(0xFFF0CAB7)),
    Month.AUGUST to RecapCardColors(Color(0xFFE2D283), Color(0xFF332D0F), Color(0xFFF0E6B7)),
    Month.SEPTEMBER to RecapCardColors(Color(0xFFC2E283), Color(0xFF27330F), Color(0xFFDDF0B7)),
    Month.OCTOBER to RecapCardColors(Color(0xFF93E283), Color(0xFF15330F), Color(0xFFC1F0B7)),
    Month.NOVEMBER to RecapCardColors(Color(0xFF83E2A3), Color(0xFF0F331B), Color(0xFFB7F0CA)),
    Month.DECEMBER to RecapCardColors(Color(0xFF83E2D2), Color(0xFF0F332D), Color(0xFFB7F0E6)),
)

/** A year recap's [month] card colors for the light or dark theme: its own field, the ink on it and the plate its rows sit on. */
fun monthPalette(month: Month, darkTheme: Boolean): RecapCardColors =
    (if (darkTheme) DarkMonthCards else LightMonthCards).getValue(month)

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
