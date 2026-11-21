package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo

/** The narrowest board column that still sits beside the watch column; the board itself grows to the pane's height. */
private val MinBoardColumn = 320.dp
private val WatchColumnWidth = 280.dp
private val ColumnGap = Spacing.xl

/** The slot under the board for Next step: kept when the button is gone, so the board does not grow on the last step. */
private val NextStepSlot = 48.dp
private val NextStepMinWidth = 200.dp

/** A pane at least this wide has the two columns side by side; a narrower one is a single column. */
private val TwoPane = MinBoardColumn + ColumnGap + WatchColumnWidth

/**
 * The technique intro (spec §2.7) over [intro], walked through by the player: each variant opens on its evidence and
 * Next step shows the squares landing, then the next variant (user-check rounds 4 and 5: autoplay ran too fast to
 * read, and the plain position before the evidence told nothing). Skip and Start practice call
 * [IntroControls.onStart]; in a [replay] over a live board Close and Back to board 1 call [IntroControls.onClose]. The
 * walk is this composition's: leaving the intro and coming back starts it again from the first variant.
 */
@Composable
internal fun IntroWatch(
    chapter: PracticeChapter,
    progress: TechniqueProgress,
    showBack: Boolean,
    onClose: () -> Unit,
    intro: TechniqueIntro,
    replay: Boolean,
    controls: IntroControls,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val player = remember(intro, reduced) { IntroPlayer(intro.entries, reduced, scope) }
    val variant by player.variant.collectAsState()
    val frame by player.frame.collectAsState()
    val leave = if (replay) controls.onClose else controls.onStart
    WatchPane(
        chapter = chapter,
        progress = progress,
        showBack = showBack,
        onClose = onClose,
        entries = intro.entries,
        variant = variant,
        frame = frame,
        replay = replay,
        demo = controls.demo,
        onSelect = player::select,
        onSeek = player::seek,
        onReplay = player::replay,
        onNext = player::next,
        onLeave = leave,
        onPrimary = leave,
        modifier = modifier,
    )
}

/**
 * The intro's screen, stateless: a top bar ("Technique intro" and Skip intro, or Close in a [replay]), the title row
 * ("Watch first", no pips), then the demo of [entries]`[variant]` at [frame], drawn by [demo] under the watching band
 * (the caption of the frame's step) and over Next step ([onNext], while there is a step or variant after it), and the
 * [WatchColumn] beside it on a wide pane or below it on a narrow one. [onLeave] is the top bar's button, [onPrimary]
 * the column's Start practice (Back to board 1 in a replay).
 */
@Composable
internal fun WatchPane(
    chapter: PracticeChapter,
    progress: TechniqueProgress,
    showBack: Boolean,
    onClose: () -> Unit,
    entries: List<IntroEntry>,
    variant: Int,
    frame: Int,
    replay: Boolean,
    demo: @Composable (IntroDemo, Int, Modifier) -> Unit,
    onSelect: (Int) -> Unit,
    onSeek: (Int) -> Unit,
    onReplay: () -> Unit,
    onNext: () -> Unit,
    onLeave: () -> Unit,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = entries[variant]
    val primary = if (replay) "Back to board 1" else "Start practice"
    BoxWithConstraints(modifier.fillMaxSize().testTag("train.watch")) {
        val wide = maxWidth >= TwoPane
        Column(Modifier.fillMaxSize().padding(bottom = Spacing.lg)) {
            TopBar(replay, showBack, onClose, onLeave)
            PracticeHeader(chapter, progress, showBack = false, onClose = onClose, watching = true)
            Spacer(Modifier.height(Spacing.sm))
            val next = onNext.takeIf { hasNextStep(variant, frame, entry.frames, entries.size) }
            val band = @Composable { WatchBand(entry.variant.title, stepCaption(entry.demo.captions, frame)) }
            val board = @Composable { demo(entry.demo, frame, Modifier) }
            if (wide) {
                // As in practice, the band and board take the whole height the header and Next step leave, as wide as that allows.
                Row(Modifier.fillMaxWidth().weight(1f), Arrangement.spacedBy(ColumnGap, Alignment.CenterHorizontally)) {
                    Column(Modifier.weight(1f, fill = false).fillMaxHeight(), Arrangement.spacedBy(Spacing.md), Alignment.CenterHorizontally) {
                        BandOverBoard(band, board, Modifier.weight(1f, fill = false))
                        NextStep(next)
                    }
                    WatchColumn(
                        entries, variant, frame, primary, compact = false, onSelect, onSeek, onReplay, onPrimary,
                        Modifier.width(WatchColumnWidth).fillMaxHeight().verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    Arrangement.spacedBy(Spacing.md),
                    Alignment.CenterHorizontally,
                ) {
                    BandOverBoard(band, board)
                    NextStep(next)
                    WatchColumn(entries, variant, frame, primary, compact = true, onSelect, onSeek, onReplay, onPrimary)
                }
            }
        }
    }
}

/** "Technique intro" with the back arrow where the phone has one, and the button that leaves: Skip intro, or Close over a live board. */
@Composable
private fun TopBar(replay: Boolean, showBack: Boolean, onClose: () -> Unit, onLeave: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) BackButton(onClose)
        Text(
            "Technique intro",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onLeave, Modifier.testTag(if (replay) "train.watch.close" else "train.watch.skip")) {
            Text(if (replay) "Close" else "Skip intro")
        }
    }
}

/** Next step, centered under the board; without [onNext] (nothing after the step shown) its slot stays empty. */
@Composable
private fun NextStep(onNext: (() -> Unit)?) {
    Box(Modifier.heightIn(min = NextStepSlot), contentAlignment = Alignment.Center) {
        if (onNext != null) {
            SecondaryButton("Next step", onNext, Modifier.widthIn(min = NextStepMinWidth).testTag("train.watch.next"))
        }
    }
}

/** The watching band: the variant's [title], and under it the [caption] of the frame shown, which rises in each time it changes. */
@Composable
internal fun WatchBand(title: String, caption: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    BandFrame(scheme.surfaceVariant, "train.watch.band", modifier) {
        Icon(PuzzleIcons.Visibility, contentDescription = null, tint = scheme.onSurface)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Watching: $title", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            key(caption) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.rise().testTag("train.watch.caption"),
                )
            }
        }
    }
}
