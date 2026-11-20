package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** What the crowd is doing: waiting for the answer, parting for it, holding for a settling month, or slumped. */
internal enum class CrowdState { Reading, Loaded, Settling, Unavailable }

/** Friends drawn when nobody is named: a year has no per-friend data, and a read still out knows none. */
internal const val AnonymousDots = 8

/** The most avatars the crowd draws; the card carries no more names. */
internal const val MaxCrowd = 30

/** From this many friends the avatars shrink so the crowd stays inside its stage. */
internal const val CrowdManyThreshold = 12

private val FewAvatar = 44.dp
private val ManyAvatar = 32.dp
private val YouSize = 72.dp
private const val GoldenAngle = 2.3999631f
private const val SlumpDp = 10f
private const val SlumpAlpha = 0.5f
private const val PartMinDp = 70
private const val PartSpreadDp = 28

/** When the answer's staging ends on the clock, ms after the crowd has gathered. */
internal const val ActEndMs = 3_200f

/**
 * The crowd's two clocks. [gatherMs] runs from the first frame until everyone has drifted in; [actMs] is -1 until the
 * answer is known and the crowd has gathered, then runs 0..[ActEndMs]: the beats, the part and everything that stamps in
 * after. A read that answers late starts [actMs] where the crowd already stands, so nobody drifts in twice.
 */
@Stable
internal class FriendsClock(val gatherEndMs: Int, instant: Boolean, answered: Boolean) {
    var gatherMs by mutableFloatStateOf(if (instant) gatherEndMs.toFloat() else 0f)
    var actMs by mutableFloatStateOf(if (instant && answered) ActEndMs else -1f)
    val started: Boolean get() = actMs >= 0f
}

@Composable
internal fun rememberFriendsClock(friends: Int, answered: Boolean): FriendsClock {
    val instant = rememberReducedMotion() || entranceSettled()
    val clock = remember { FriendsClock(FriendsMotion.gatherEndMs(friends), instant, answered) }
    LaunchedEffect(instant) {
        if (instant) clock.gatherMs = clock.gatherEndMs.toFloat() else animate(0f, clock.gatherEndMs.toFloat(), animationSpec = tween(clock.gatherEndMs, easing = LinearEasing)) { v, _ -> clock.gatherMs = v }
    }
    LaunchedEffect(answered, instant) {
        if (!answered) return@LaunchedEffect
        if (instant) {
            clock.actMs = ActEndMs
        } else {
            snapshotFlow { clock.gatherMs >= clock.gatherEndMs }.collect { gathered ->
                if (gathered) {
                    animate(0f, ActEndMs, animationSpec = tween(ActEndMs.toInt(), easing = LinearEasing)) { v, _ -> clock.actMs = v }
                }
            }
        }
    }
    return clock
}

/** The two-letter initials of [name]: the first letter of up to two words; a blank name reads "?". */
internal fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** How many avatars the crowd draws for [friendCount] friends. */
internal fun crowdSize(friendCount: Int): Int = if (friendCount <= 0) AnonymousDots else min(friendCount, MaxCrowd)

/**
 * The pile of avatars around "You" on the Friends field: they drift in one after another, beat twice and part for the
 * answer. A settling month's crowd never parts: "You" fades for the calendar tile instead. Reading wobbles the avatars
 * and Unavailable slumps them. Avatars carry [names]' initials, or nothing when nobody is named. Decoration only: the
 * card's own text says everything, so a screen reader skips the crowd.
 */
@Composable
internal fun FriendsCrowd(
    names: List<String>,
    friendCount: Int,
    state: CrowdState,
    clock: FriendsClock,
    modifier: Modifier = Modifier,
) {
    val count = crowdSize(friendCount)
    val many = count > CrowdManyThreshold
    val avatar = if (many) ManyAvatar else FewAvatar
    val ink = PaletteEntry.Friends.ink
    val ringRadius = YouSize.value * 0.5f
    val wobbling = state == CrowdState.Reading && !(rememberReducedMotion() || entranceSettled())
    // Only a waiting crowd runs a clock that never ends, so every other state lets a test or the system go idle.
    val wobble = if (wobbling) {
        val bob by rememberInfiniteTransition(label = "friends.wobble").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(FriendsMotion.WobbleMs, easing = LinearEasing), RepeatMode.Reverse),
            label = "wobble",
        )
        bob
    } else {
        0f
    }
    val extent = 2 * (radiusOf(count - 1, many) + avatar.value)
    val step = FriendsMotion.gatherStepMs(count)
    Box(
        modifier
            .requiredSize(extent.dp, extent.dp)
            .clearAndSetSemantics {}
            .drawBehind {
                // Two rings leave "You" as the crowd parts, 140ms apart.
                if (state != CrowdState.Loaded) return@drawBehind
                repeat(2) { ring ->
                    val t = ((clock.actMs - FriendsMotion.PartAtMs - ring * RecapMotion.RingStagger) / FriendsMotion.RingDuration).coerceIn(0f, 1f)
                    if (t > 0f && t < 1f) {
                        drawCircle(
                            ink.copy(alpha = lerp(RecapMotion.RingAlphaFrom, 0f, t)),
                            radius = ringRadius.dp.toPx() * lerp(1f, FriendsMotion.RingScale, t),
                            center = Offset(size.width / 2, size.height / 2),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        repeat(count) { index ->
            val angle = index * GoldenAngle + 0.6f
            val at = radiusOf(index, many)
            val start = (index * step).toFloat()
            Avatar(
                label = names.getOrNull(index)?.let(::initials),
                size = avatar,
                modifier = Modifier.graphicsLayer {
                    val arrive = cascade(clock.gatherMs - start)
                    val beat = beatScale(clock.actMs)
                    val part = ((clock.actMs - FriendsMotion.PartAtMs) / FriendsMotion.PartMs).coerceIn(0f, 1f).takeIf { state == CrowdState.Loaded } ?: 0f
                    val away = at + part * (PartMinDp + (index * 13) % PartSpreadDp)
                    val slump = if (state == CrowdState.Unavailable) SlumpDp.dp.toPx() * arrive else 0f
                    val bob = if (wobbling) -FriendsMotion.WobbleDp.dp.toPx() * (if (index % 2 == 0) wobble else 1f - wobble) else 0f
                    translationX = cos(angle) * away.dp.toPx()
                    translationY = sin(angle) * away.dp.toPx() + slump + bob
                    rotationX = (1f - arrive) * RecapMotion.CascadeFromRotationX
                    alpha = arrive.coerceIn(0f, 1f) * lerp(1f, 0f, part) * (if (state == CrowdState.Unavailable) SlumpAlpha else 1f)
                    scaleX = lerp(RecapMotion.CascadeFromScale, 1f, arrive) * beat
                    scaleY = scaleX
                },
            )
        }
        You(
            Modifier.graphicsLayer {
                val arrive = cascade(clock.gatherMs)
                val leaves = when (state) {
                    CrowdState.Loaded -> ((clock.actMs - FriendsMotion.PartAtMs) / FriendsMotion.PartMs).coerceIn(0f, 1f)
                    CrowdState.Settling -> (clock.actMs / FriendsMotion.PartMs).coerceIn(0f, 1f)
                    else -> 0f
                }
                alpha = arrive.coerceIn(0f, 1f) * (1f - leaves) * (if (state == CrowdState.Unavailable) SlumpAlpha else 1f)
                scaleX = beatScale(clock.actMs)
                scaleY = scaleX
                translationY = if (state == CrowdState.Unavailable) SlumpDp.dp.toPx() else 0f
            },
        )
    }
}

/** Where the avatar at [index] sits from "You", dp: a sunflower spiral, wider for the few big avatars. */
private fun radiusOf(index: Int, many: Boolean): Float =
    if (many) 52f + 14f * sqrt(index.coerceAtLeast(0).toFloat()) else 70f + 16f * sqrt(index.coerceAtLeast(0).toFloat())

private val DeckSpring = spring<Float>(RecapMotion.DeckSpringDamping, RecapMotion.DeckSpringStiffness).vectorize(Float.VectorConverter)

/** How far an avatar that started [sinceMs] ago has come on the DeckSpring, 0 to about 1 (it overshoots a little). */
private fun cascade(sinceMs: Float): Float =
    if (sinceMs <= 0f) 0f else DeckSpring.getValueFromNanos((sinceMs * 1_000_000L).toLong(), AnimationVector1D(0f), AnimationVector1D(1f), AnimationVector1D(0f)).value

/** The crowd swells and settles twice, one [FriendsMotion.BeatMs] each, once the answer is in; 1 before and after. */
private fun beatScale(actMs: Float): Float {
    if (actMs < 0f || actMs >= FriendsMotion.PartAtMs) return 1f
    val t = (actMs % FriendsMotion.BeatMs) / FriendsMotion.BeatMs
    return 1f + (MotionTokens.PulseScale - 1f) * sin(PI.toFloat() * t)
}

@Composable
private fun Avatar(label: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = PaletteEntry.Friends.plate,
        border = BorderStroke(1.5.dp, PaletteEntry.Friends.ink),
    ) {
        Box(contentAlignment = Alignment.Center) {
            label?.let {
                Text(it, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = if (size == FewAvatar) 13.sp else 11.sp), color = PaletteEntry.Friends.ink)
            }
        }
    }
}

@Composable
private fun You(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(YouSize), shape = CircleShape, color = PaletteEntry.Friends.ink) {
        Box(contentAlignment = Alignment.Center) {
            Text("You", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PaletteEntry.Friends.plate)
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun FriendsCrowdPreview() = PuzzleBoxTheme {
    val names = listOf("Maya R", "Dev", "Jo", "Priya S", "Sam K", "Lee", "Omar", "Tess", "Rui")
    Surface(color = PaletteEntry.Friends.plate) {
        Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
            FriendsCrowd(names, names.size, CrowdState.Reading, rememberFriendsClock(names.size, answered = false))
        }
    }
}
