package com.ivandsky.puzzlebox.train

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.train.practice.BoardHold
import com.ivandsky.puzzlebox.train.practice.BoardRow
import com.ivandsky.puzzlebox.train.practice.BoardRowKind
import com.ivandsky.puzzlebox.train.practice.ChapterOutcome
import com.ivandsky.puzzlebox.train.practice.ChapterResult
import com.ivandsky.puzzlebox.train.practice.IntroEntry
import com.ivandsky.puzzlebox.train.practice.PracticeChapter
import com.ivandsky.puzzlebox.train.practice.PracticePane
import com.ivandsky.puzzlebox.train.practice.PracticeState
import com.ivandsky.puzzlebox.train.practice.StandInIntroDemo
import com.ivandsky.puzzlebox.train.practice.StandInGrid
import com.ivandsky.puzzlebox.train.practice.StandInPracticeBoard
import com.ivandsky.puzzlebox.train.practice.WatchPane
import com.ivandsky.puzzlebox.train.practice.standInChapter
import com.ivandsky.puzzlebox.train.practice.standInSpotlight
import com.ivandsky.puzzlebox.train.tab.TechniqueList
import com.ivandsky.puzzlebox.train.tab.previewGames
import com.ivandsky.puzzlebox.train.tab.previewTechniques
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review renders of the Train pane (ADR-0033: rendered on demand, never committed): the technique list, and a practice
 * board before a move, after a Right, after an undone Wrong move, finished with its hold running, and the chapter's
 * result page. The board is a stand-in grid, as this module
 * sees no game's board. Each render sits in the padding the Games screen gives it: the phone's page inset, or the
 * tablet's 400dp list column with the practice board or the placeholder beside it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TrainPhoneGoldenScreenshots : TrainGoldenScreenshots("phone") {
    @Test fun `list light`() = capture("list", dark = false) { Page { List(open = false) } }
    @Test fun `list dark`() = capture("list", dark = true) { Page { List(open = false) } }

    @Test fun `practice find next move light`() = capture("practice-find", dark = false) { Page { Practice(FindNext, showBack = true) } }
    @Test fun `practice find next move dark`() = capture("practice-find", dark = true) { Page { Practice(FindNext, showBack = true) } }

    @Test fun `practice right light`() = capture("practice-right", dark = false) { Page { Practice(Right, showBack = true, Three) } }
    @Test fun `practice right dark`() = capture("practice-right", dark = true) { Page { Practice(Right, showBack = true, Three) } }

    @Test fun `practice wrong light`() = capture("practice-wrong", dark = false) { Page { Practice(Wrong, showBack = true) } }
    @Test fun `practice wrong dark`() = capture("practice-wrong", dark = true) { Page { Practice(Wrong, showBack = true) } }

    @Test fun `practice done light`() = capture("practice-done", dark = false) { Page { Practice(Done, showBack = true, Three, MidHold) } }
    @Test fun `practice done dark`() = capture("practice-done", dark = true) { Page { Practice(Done, showBack = true, Three, MidHold) } }
    @Test fun `practice done unsolved light`() = capture("practice-done-unsolved", dark = false) { Page { Practice(DoneUnsolved, showBack = true, Three, MidHold) } }
    @Test fun `practice keep going light`() = capture("practice-keep-going", dark = false) { Page { Practice(KeepGoing, showBack = true) } }
    @Test fun `practice easier light`() = capture("practice-easier", dark = false) { Page { Practice(Easier, showBack = true) } }
    @Test fun `practice not yet light`() = capture("practice-not-yet", dark = false) { Page { Practice(NotYet, showBack = true) } }
    @Test fun `practice fits light`() = capture("practice-fits", dark = false) { Page { Practice(Fits, showBack = true, Three) } }
    @Test fun `practice review board light`() = capture("practice-review", dark = false) { Page { Practice(FindNext, showBack = true, chapter = standInChapter(board = 5)) } }
    @Test fun `chapter result light`() = capture("chapter-result", dark = false) { Page { Practice(Result, showBack = true) } }
    @Test fun `chapter result dark`() = capture("chapter-result", dark = true) { Page { Practice(Result, showBack = true) } }
    @Test fun `empty light`() = capture("empty", dark = false) { Page { Practice(PracticeState.Empty, showBack = true) } }
    @Test fun `watch with one variant light`() = capture("watch", dark = false) { Page { Watch(count = 1, showBack = true, frame = LastFrame) } }
    @Test fun `watch with two variants light`() = capture("watch-variants", dark = false) { Page { Watch(count = 2, showBack = true) } }
    @Test fun `watch with two variants dark`() = capture("watch-variants", dark = true) { Page { Watch(count = 2, showBack = true) } }
    @Test fun `watch replay light`() = capture("watch-replay", dark = false) { Page { Watch(count = 1, showBack = true, replay = true, frame = LastFrame) } }
    @Test fun `needs connection light`() = capture("needs-connection", dark = false) { Page { Practice(PracticeState.NeedsConnection, showBack = true) } }
    @Test fun `cant load light`() = capture("cant-load", dark = false) { Page { Practice(PracticeState.CantLoad(alternative = 918), showBack = true) } }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TrainTabletGoldenScreenshots : TrainGoldenScreenshots("tablet") {
    @Test fun `list with the placeholder light`() = capture("list", dark = false) { Beside(open = false) { Placeholder() } }

    @Test fun `practice find next move light`() = capture("practice-find", dark = false) { Beside(open = true) { Practice(FindNext, showBack = false) } }
    @Test fun `practice find next move dark`() = capture("practice-find", dark = true) { Beside(open = true) { Practice(FindNext, showBack = false) } }

    @Test fun `practice right light`() = capture("practice-right", dark = false) { Beside(open = true) { Practice(Right, showBack = false, Three) } }
    @Test fun `practice right dark`() = capture("practice-right", dark = true) { Beside(open = true) { Practice(Right, showBack = false, Three) } }

    @Test fun `practice done light`() = capture("practice-done", dark = false) { Beside(open = true) { Practice(Done, showBack = false, Three, MidHold) } }
    @Test fun `watch with one variant light`() = capture("watch", dark = false) { Beside(open = true) { Watch(count = 1, showBack = false, frame = LastFrame) } }
    @Test fun `watch with two variants light`() = capture("watch-variants", dark = false) { Beside(open = true) { Watch(count = 2, showBack = false) } }
    @Test fun `watch replay light`() = capture("watch-replay", dark = false) { Beside(open = true) { Watch(count = 1, showBack = false, replay = true, frame = LastFrame) } }
    @Test fun `chapter result light`() = capture("chapter-result", dark = false) { Beside(open = true) { Practice(Result, showBack = false) } }
    @Test fun `chapter result dark`() = capture("chapter-result", dark = true) { Beside(open = true) { Practice(Result, showBack = false) } }
}

abstract class TrainGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    internal fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.showGolden(dark, content)
        rule.settleAndCapture("train", name, device, dark)
    }

    internal companion object {
        val FindNext = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.FindNextMove))
        val Right = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Right(standInSpotlight(), trained = true)))
        val Wrong = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Wrong("Two queens would touch.")))
        val Done = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Done(standInSpotlight())))
        val DoneUnsolved = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Done(standInSpotlight(), needsOtherTechnique = true)))
        val KeepGoing = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.KeepGoing(left = 2, trained = true)))
        val Easier = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Easier("Naked single", standInSpotlight())))
        val NotYet = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Easier("Line lock", standInSpotlight())))
        val Fits = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Right(standInSpotlight(), trained = false, technique = "Naked single")))
        val MidHold = BoardHold(remainingMs = 900, waitsForTap = false)
        /** The stand-in demo's last frame: the squares landed, so the main button is Start practice. */
        const val LastFrame = 3
        val Result = PracticeState.Result(
            ChapterResult(
                technique = TrainerTechnique(TechniqueId("line-lock"), "Line lock", "A region sits inside one line."),
                next = TrainerTechnique(TechniqueId("pointing-pair"), "Pointing pair", "A pair points."),
                outcome = ChapterOutcome.NewlyMastered,
                boards = 5,
                boardsDone = 5,
                rights = 14,
                shown = 1,
                slips = 1,
                undone = 3,
                best = 9,
                rows = listOf(
                    BoardRow(1, BoardRowKind.Clean, 3),
                    BoardRow(2, BoardRowKind.Undone, 3, undone = 3),
                    BoardRow(3, BoardRowKind.Clean, 2),
                    BoardRow(4, BoardRowKind.Shown, 3),
                    BoardRow(5, BoardRowKind.Slip, 3),
                ),
                allMastered = false,
                techniques = 4,
            ),
        )
        val Three = TechniqueProgress(TechniqueProgress.Mark.Practicing, inRow = 3, answeredAtMs = 1L)
        val Two = TechniqueProgress(TechniqueProgress.Mark.Practicing, inRow = 2, answeredAtMs = 1L)

        @Composable
        fun List(open: Boolean) = TechniqueList(
            games = previewGames,
            selected = previewGames.first().id,
            techniques = previewTechniques,
            openId = TechniqueId("line-lock").takeIf { open },
            onSelectGame = {},
            onOpen = {},
            modifier = Modifier.fillMaxSize(),
        )

        /** The intro's watching screen, [count] variants, variant 0 on [frame]: 1 is the evidence (with Next step), [LastFrame] its last step (Start practice). */
        @Composable
        fun Watch(count: Int, showBack: Boolean, replay: Boolean = false, frame: Int = 1) = WatchPane(
            chapter = standInChapter(board = 1, boards = 5),
            progress = Two,
            showBack = showBack,
            onClose = {},
            entries = (0 until count).map {
                IntroEntry(
                    IntroVariant("Variant $it", "A color in one line leaves the rest of the line.", "Find a color all in one row.", "Cross out the rest of the row."),
                    StandInIntroDemo(it, listOf("Here is the position.", "The evidence lights up.", "The first square lands.", "The line is done.")),
                )
            },
            variant = 0,
            frame = frame,
            replay = replay,
            demo = { _, _, modifier -> StandInGrid(modifier) },
            onSelect = {},
            onSeek = {},
            onReplay = {},
            onNext = {},
            onLeave = {},
            onPrimary = {},
        )

        @Composable
        fun Practice(
            state: PracticeState,
            showBack: Boolean,
            progress: TechniqueProgress = Two,
            hold: BoardHold? = null,
            chapter: PracticeChapter = standInChapter(board = 2, boards = 5),
        ) = PracticePane(
            chapter = chapter,
            state = state,
            progress = progress,
            showBack = showBack,
            onClose = {},
            board = { _, modifier -> StandInGrid(modifier) },
            onTryAgain = {},
            onTryAnother = {},
            hold = hold,
            onSkip = {},
            onNextChapter = {},
            onPracticeAgain = {},
        )

        @Composable
        fun Placeholder() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Pick a technique", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        /** The phone's Train page: the pager page's side inset and top gap. */
        @Composable
        fun Page(content: @Composable () -> Unit) =
            Box(Modifier.fillMaxSize().padding(horizontal = Spacing.lg).padding(top = Spacing.sm)) { content() }

        /** The tablet's two panes as the Library lays them: the list column, a gap, then the right pane. */
        @Composable
        fun Beside(open: Boolean, pane: @Composable () -> Unit) = Row(
            Modifier.fillMaxSize().padding(start = Spacing.xl, top = Spacing.lg, end = Spacing.xxl, bottom = Spacing.xl),
        ) {
            Box(Modifier.width(400.dp).padding(horizontal = 6.dp).padding(top = Spacing.sm)) { List(open) }
            Spacer(Modifier.width(Spacing.xl))
            Box(Modifier.weight(1f)) { pane() }
        }
    }
}
