package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo

private val BoardColumn = 380.dp
private val WatchColumnWidth = 280.dp
private val ColumnGap = Spacing.xl

/** A pane at least this wide has the two columns side by side; a narrower one is a single column. */
private val TwoPane = BoardColumn + ColumnGap + WatchColumnWidth

/**
 * The technique intro (spec §2.7) over [intro], playing: the demo starts at once (and under reduced motion rests on its
 * last frame, with Replay stepping through it), the next variant follows by itself. Skip and Start practice call
 * [IntroControls.onStart]; in a [replay] over a live board Close and Back to board 1 call [IntroControls.onClose]. The
 * playback is this composition's: leaving the intro and coming back plays it again from the first variant.
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
        onLeave = leave,
        onPrimary = leave,
        modifier = modifier,
    )
}

/**
 * The intro's screen, stateless: a top bar ("Technique intro" and Skip intro, or Close in a [replay]), the title row
 * ("Watch first", no pips), then the demo of [entries]`[variant]` at [frame], drawn by [demo] under the watching band
 * (the frame's caption), and the [WatchColumn] beside it on a wide pane or below it on a narrow one. [onLeave] is the
 * top bar's button, [onPrimary] the column's Start practice (Back to board 1 in a replay).
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
            val board = @Composable {
                BandOverBoard(
                    band = { WatchBand(entry.variant.title, entry.demo.captions[frame]) },
                    board = { demo(entry.demo, frame, Modifier) },
                )
            }
            if (wide) {
                Row(Modifier.fillMaxWidth().weight(1f), Arrangement.spacedBy(ColumnGap, Alignment.CenterHorizontally)) {
                    Box(Modifier.width(BoardColumn)) { board() }
                    WatchColumn(
                        entries, variant, frame, primary, compact = false, onSelect, onSeek, onReplay, onPrimary,
                        Modifier.width(WatchColumnWidth).verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    board()
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
