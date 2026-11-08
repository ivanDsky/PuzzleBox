package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDictionary

/** What the dictionary says of the text typed so far. */
internal enum class WordCheck { Empty, Prefix, Word, Unknown }

/** [text] is a dictionary word, the start of one, or neither. */
internal fun wordCheck(text: String): WordCheck = when {
    text.isEmpty() -> WordCheck.Empty
    StrandsDictionary.contains(text) -> WordCheck.Word
    StrandsDictionary.hasPrefix(text) -> WordCheck.Prefix
    else -> WordCheck.Unknown
}

/** The extend handle's entrance: one finite pass when it appears, never a loop. */
internal object ExtendHandleMotion {
    const val FROM_SCALE = 0.6f

    val spec: FiniteAnimationSpec<Float> = repeatable(
        iterations = 1,
        animation = tween(MotionTokens.DurationMedium),
    )
}

/**
 * The word field (`strands.editor.word`): letters only, checked against the dictionary as
 * they are typed; the verdict is on `strands.editor.word.check` as its state description.
 */
@Composable
internal fun StrandsWordField(controller: StrandsEditorController, modifier: Modifier = Modifier) {
    val word by controller.word.collectAsState()
    val check = remember(word) { wordCheck(word) }
    OutlinedTextField(
        value = word,
        onValueChange = controller::typeWord,
        singleLine = true,
        isError = check == WordCheck.Unknown,
        supportingText = {
            Text(
                text = when (check) {
                    WordCheck.Empty -> ""
                    WordCheck.Prefix -> "Keep typing"
                    WordCheck.Word -> "In the dictionary"
                    WordCheck.Unknown -> "Not in the dictionary"
                },
                modifier = Modifier
                    .testTag("strands.editor.word.check")
                    .semantics { stateDescription = check.name },
            )
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("strands.editor.word"),
    )
}

/**
 * The "+N" handle (`strands.editor.extend`): N cells the word has beyond its path, a tap
 * grows the path by one. It pops in once when it appears; reduced motion skips the pop.
 */
@Composable
internal fun StrandsExtendHandle(count: Int, onExtend: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val pop = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, ExtendHandleMotion.spec) }
    Surface(
        onClick = onExtend,
        shape = PillShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .graphicsLayer {
                val scale = ExtendHandleMotion.FROM_SCALE + (1f - ExtendHandleMotion.FROM_SCALE) * pop.value
                scaleX = scale
                scaleY = scale
                alpha = pop.value
            }
            .sizeIn(minWidth = Spacing.xxl, minHeight = Spacing.xxl)
            .testTag("strands.editor.extend")
            .semantics { stateDescription = count.toString() },
    ) {
        Text(
            text = "+$count",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Three cells missing", showBackground = true, group = "Strands extend handle")
@Composable
private fun StrandsExtendHandlePreview() {
    PuzzleBoxTheme { StrandsExtendHandle(count = 3, onExtend = {}, modifier = Modifier.padding(Spacing.md)) }
}
