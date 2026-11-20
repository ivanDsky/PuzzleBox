package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * [text] in large letters that each rise into place on the Stamp spring, one after another ([RecapMotion] Kinetic),
 * the first [startMs] after the title is composed. Letters stay together in their word, so a title wraps between words
 * and never inside one. Reduced motion draws the letters at rest. A screen reader reads [text] whole.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecapKineticTitle(text: String, fontSize: TextUnit, modifier: Modifier = Modifier, startMs: Int = 0) {
    val reduced = rememberReducedMotion()
    val words = text.split(' ').filter { it.isNotEmpty() }
    val stagger = (RecapMotion.KineticStaggerBudget / words.sumOf { it.length }.coerceAtLeast(1))
        .coerceIn(RecapMotion.KineticStaggerMin, RecapMotion.KineticStaggerMax)
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = fontSize,
        lineHeight = fontSize * LineHeight,
        fontWeight = FontWeight.Bold,
        letterSpacing = fontSize * Tracking,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    FlowRow(
        modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        horizontalArrangement = Arrangement.spacedBy((fontSize.value * WordGap).dp),
    ) {
        var before = 0
        for (word in words) {
            val first = before
            before += word.length
            Row {
                word.forEachIndexed { i, letter -> KineticLetter(letter, style, startMs + (first + i) * stagger, reduced) }
            }
        }
    }
}

@Composable
private fun KineticLetter(letter: Char, style: TextStyle, delayMs: Int, reduced: Boolean) {
    val rise = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        delay(delayMs.toLong())
        rise.animateTo(1f, spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness))
    }
    // The line box is pinned to the style's line height: the font's own metrics would make it half as tall again.
    val line = with(LocalDensity.current) { style.lineHeight.toDp() }
    Box(Modifier.height(line).clipToBounds(), contentAlignment = Alignment.Center) {
        Text(
            letter.toString(),
            style = style,
            modifier = Modifier.wrapContentHeight(unbounded = true).graphicsLayer { translationY = (1f - rise.value) * RecapMotion.KineticRise * size.height },
        )
    }
}

private const val LineHeight = 1.05f
private const val Tracking = -0.02f

/** The space between words, in dp per sp of font size. */
private const val WordGap = 0.28f
