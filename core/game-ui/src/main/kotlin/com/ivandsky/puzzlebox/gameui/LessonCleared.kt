package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.delay

/** The words a cleared lesson says, in turn, so consecutive lessons never repeat one. */
private val LessonWords = listOf("Nice!", "Great!", "You got it!")

/**
 * The stamp row of a cleared lesson (spec A21, identity §2.7): a short beat after the
 * solving move (the board's sweep has a head start), [brand]'s solid glyph badge Stamps
 * in beside a word in the accent. The word rotates by [lessonIndex]. Reduced motion (and
 * the preview inspector) shows the row at once. It only draws; the host owns the hold.
 */
@Composable
internal fun LessonCleared(brand: GameBrand, lessonIndex: Int, modifier: Modifier = Modifier) {
    val instant = rememberReducedMotion() || LocalInspectionMode.current
    var stamped by remember { mutableStateOf(instant) }
    LaunchedEffect(instant) {
        if (!instant) delay(MotionTokens.DurationShort.toLong())
        stamped = true
    }
    Box(modifier.testTag("tutorial.success"), contentAlignment = Alignment.Center) {
        Stamp(visible = stamped) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameGlyph(brand, GameGlyphSize.Badge, solid = true)
                Text(
                    text = lessonWord(lessonIndex),
                    style = MaterialTheme.typography.titleLarge,
                    color = brand.accent.colors().accent,
                    modifier = Modifier.testTag("tutorial.success_word"),
                )
            }
        }
    }
}

private fun lessonWord(lessonIndex: Int): String = LessonWords[lessonIndex.mod(LessonWords.size)]
