package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.format.TextStyle as MonthStyle
import java.util.Locale
import kotlinx.coroutines.delay

/** A game's brand for the page that stamps it; the route provides the registry's, a screen with none shows the neutral one. */
internal val LocalGameBrands = compositionLocalOf<(GameId) -> GameBrand> { { GameBrand.Neutral } }

// When each piece of the answer lands, ms after the crowd has gathered: the badge as the crowd parts, the chip at A + 760,
// the name after the badge and the points once the name is in.
private const val BadgeAtMs = 720
private const val ChipAtMs = 760
private const val NameAtMs = 900
private const val PointsAtMs = 1_100
private const val TileAtMs = 120
private const val ChipFromScale = 0.4f
private const val NameWrapOver = 18
private const val MiniAvatars = 4
private val BadgeSize = 136.dp

/** Springs 0 to 1 on the Stamp spring [delayMs] after the crowd's answer starts; reduced motion fades in over one ReducedFade. */
@Composable
private fun rememberStamp(started: Boolean, delayMs: Int): State<Float> {
    val reduced = rememberReducedMotion()
    val settled = entranceSettled()
    val progress = remember { Animatable(if ((reduced || settled) && started) 1f else 0f) }
    LaunchedEffect(started, reduced, settled) {
        when {
            !started -> Unit
            settled -> progress.snapTo(1f)
            reduced -> progress.animateTo(1f, tween(MotionTokens.ReducedFadeDuration))
            else -> {
                delay(delayMs.toLong())
                progress.animateTo(1f, spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness))
            }
        }
    }
    return progress.asState()
}

private fun Modifier.stamped(progress: State<Float>, fromScale: Float): Modifier = graphicsLayer {
    val p = progress.value
    alpha = p.coerceIn(0f, 1f)
    scaleX = lerp(fromScale, 1f, p)
    scaleY = scaleX
}

/** The winning game's glyph stamped in where the crowd parted, on the game's own colour. */
@Composable
internal fun FriendsBadge(game: GameId, started: Boolean, modifier: Modifier = Modifier) {
    val brand = LocalGameBrands.current(game)
    val ink = PaletteEntry.Friends.ink
    val stamp = rememberStamp(started, BadgeAtMs)
    Box(
        modifier
            .stamped(stamp, MotionTokens.StampFromScale)
            .size(BadgeSize)
            .clip(RoundedCornerShape(40.dp))
            .background(brand.accent.colors().accent)
            .border(2.dp, ink, RoundedCornerShape(40.dp))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(brand.glyph, contentDescription = null, modifier = Modifier.size(72.dp), tint = ink)
    }
}

/** The game's name in letters that rise one after another, wrapping by word past 18 characters and never clipped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KineticName(name: String, started: Boolean, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val stagger = (RecapMotion.KineticStaggerBudget / name.length.coerceAtLeast(1)).coerceIn(RecapMotion.KineticStaggerMin, RecapMotion.KineticStaggerMax)
    val words = if (name.length > NameWrapOver) name.split(' ') else listOf(name)
    var letter = 0
    // One text for a screen reader, whatever the letters are doing.
    FlowRow(
        modifier.semantics { text = AnnotatedString(name) },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        words.forEach { word ->
            Row(Modifier.clearAndSetSemantics {}) {
                word.forEach { char ->
                    KineticLetter(char, started, NameAtMs + letter++ * stagger, style)
                }
            }
        }
    }
}

@Composable
private fun KineticLetter(char: Char, started: Boolean, delayMs: Int, style: TextStyle) {
    val rise = rememberStamp(started, delayMs)
    Text(
        text = char.toString(),
        style = style,
        color = PaletteEntry.Friends.ink,
        modifier = Modifier.graphicsLayer {
            val p = rise.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * RecapMotion.KineticRise * size.height
        },
    )
}

/** "412 pts among friends": the number rolls up on CountHero, the rest sits beside it at a quieter size. */
@Composable
internal fun FriendsPoints(points: Long, unit: String, started: Boolean, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val settled = entranceSettled()
    val roll = remember { Animatable(if ((reduced || settled) && started) 1f else 0f) }
    LaunchedEffect(started, reduced, settled) {
        when {
            !started -> Unit
            reduced || settled -> roll.snapTo(1f)
            else -> roll.animateTo(1f, tween(RecapMotion.CountHeroDuration, delayMillis = PointsAtMs + RecapMotion.CountHeroDelay, easing = MotionTokens.EasingEmphasizedDecelerate))
        }
    }
    val shown = (points * roll.value).toLong()
    val final = "${"%,d".format(Locale.ROOT, points)} $unit"
    val number = MaterialTheme.typography.displaySmall.tabular().copy(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
    Text(
        text = buildAnnotatedString {
            withStyle(number.toSpanStyle()) { append("%,d".format(Locale.ROOT, shown)) }
            withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)) { append(" $unit") }
        },
        // The count rolls, so a screen reader reads the settled line, never a step of the roll.
        modifier = modifier.graphicsLayer { alpha = if (started) 1f else 0f }.semantics { text = AnnotatedString(final) },
        style = MaterialTheme.typography.titleMedium,
        color = PaletteEntry.Friends.ink,
        textAlign = TextAlign.Center,
    )
}

/** "Among 9 friends" on a pill that springs in from scale .4, with the first few friends' initials beside it. */
@Composable
internal fun FriendsChip(names: List<String>, friendCount: Int, started: Boolean, modifier: Modifier = Modifier) {
    val stamp = rememberStamp(started, ChipAtMs)
    val ink = PaletteEntry.Friends.ink
    val plate = PaletteEntry.Friends.plate
    Row(
        modifier
            .stamped(stamp, ChipFromScale)
            .clip(CircleShape)
            .background(ink)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            names.take(MiniAvatars).forEach { name ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(plate).border(1.5.dp, ink, CircleShape), contentAlignment = Alignment.Center) {
                    Text(initials(name).take(1), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ink)
                }
            }
        }
        Text(
            text = "Among $friendCount friends",
            modifier = Modifier.padding(start = Spacing.sm),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = plate,
        )
    }
}

/** A month still settling: the calendar tile of the day its results are final stamps in where "You" was. */
@Composable
internal fun SettlingTile(date: LocalDate, started: Boolean, modifier: Modifier = Modifier) {
    val stamp = rememberStamp(started, TileAtMs)
    val ink = PaletteEntry.Friends.ink
    Column(
        modifier
            .stamped(stamp, MotionTokens.StampFromScale)
            .width(112.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(2.dp, ink, RoundedCornerShape(20.dp))
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.month.getDisplayName(MonthStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
            modifier = Modifier.fillMaxWidth().background(PaletteEntry.Friends.plate).padding(vertical = Spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = date.dayOfMonth.toString(),
            modifier = Modifier.fillMaxWidth().background(ink).padding(vertical = Spacing.xs),
            style = MaterialTheme.typography.displaySmall.tabular().copy(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold),
            color = PaletteEntry.Friends.plate,
            textAlign = TextAlign.Center,
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun FriendsAnswerPreview() = PuzzleBoxTheme {
    Column(Modifier.background(PaletteEntry.Friends.plate).padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        FriendsBadge(GameId("zip"), started = true)
        KineticName("Mini Sudoku", started = true)
        FriendsPoints(1_980, "pts among friends", started = true)
        FriendsChip(listOf("Maya", "Dev", "Jo", "Priya"), 30, started = true)
        SettlingTile(LocalDate.of(2026, 12, 4), started = true)
    }
}
