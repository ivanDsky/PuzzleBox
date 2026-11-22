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
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo

/** The narrowest board column that still sits beside the watch column; the board itself grows to the pane's height. */
private val MinBoardColumn = 320.dp
private val WatchColumnWidth = 280.dp
private val ColumnGap = Spacing.xl

/** The main button under the board: wide enough that "Next step" and the label it turns into keep one size. */
private val MainActionMinWidth = 240.dp

/** A pane at least this wide has the two columns side by side; a narrower one is a single column. */
private val TwoPane = MinBoardColumn + ColumnGap + WatchColumnWidth

/**
 * The technique intro (spec §2.7) over [intro], walked through by the player: each variant opens on its evidence and
 * Next step shows the squares landing, then the next variant (user-check rounds 4 and 5: autoplay ran too fast to
 * read, and the plain position before the evidence told nothing). On the last step Next step turns into Start
 * practice (round 6); it and Skip intro call [IntroControls.onStart]. In a [replay] over a live board Back to the board
 * and Close call [IntroControls.onClose]. The walk is this composition's: leaving the intro and coming back starts it
 * again from the first variant.
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
 * (the caption of the frame's step) and over the main button, and the [WatchColumn] beside it on a wide pane or below
 * it on a narrow one. The main button is Next step ([onNext]) while there is a step or variant after the one shown,
 * then [onPrimary]'s Start practice (Back to the board in a replay). [onLeave] is the top bar's button.
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
    val primary = if (replay) "Back to the board" else "Start practice"
    BoxWithConstraints(modifier.fillMaxSize().testTag("train.watch")) {
        val wide = maxWidth >= TwoPane
        Column(Modifier.fillMaxSize().padding(bottom = Spacing.lg)) {
            TopBar(replay, showBack, onClose, onLeave)
            PracticeHeader(chapter, progress, showBack = false, onClose = onClose, watching = true)
            Spacer(Modifier.height(Spacing.sm))
            val next = hasNextStep(variant, frame, entry.frames, entries.size)
            val band = @Composable { WatchBand(entry.variant.title, stepCaption(entry.demo.captions, frame)) }
            val board = @Composable { demo(entry.demo, frame, Modifier) }
            if (wide) {
                // As in practice, the band and board take the whole height the header and Next step leave, as wide as that allows.
                Row(Modifier.fillMaxWidth().weight(1f), Arrangement.spacedBy(ColumnGap, Alignment.CenterHorizontally)) {
                    Column(Modifier.weight(1f, fill = false).fillMaxHeight(), Arrangement.spacedBy(Spacing.md), Alignment.CenterHorizontally) {
                        BandOverBoard(band, board, Modifier.weight(1f, fill = false))
                        MainAction(next, primary, onNext, onPrimary)
                    }
                    WatchColumn(
                        entries, variant, frame, compact = false, onSelect, onSeek, onReplay,
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
                    MainAction(next, primary, onNext, onPrimary)
                    WatchColumn(entries, variant, frame, compact = true, onSelect, onSeek, onReplay)
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

/**
 * The intro's one main button, centered under the board: Next step while [next] (a step or variant follows the one
 * shown), then [primary] (Start practice, or Back to the board in a replay) in the same place on the last step.
 */
@Composable
private fun MainAction(next: Boolean, primary: String, onNext: () -> Unit, onPrimary: () -> Unit) {
    val modifier = Modifier.widthIn(min = MainActionMinWidth)
    if (next) {
        PrimaryButton("Next step", onNext, modifier.testTag("train.watch.next"))
    } else {
        PrimaryButton(primary, onPrimary, modifier.testTag("train.watch.start"))
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
