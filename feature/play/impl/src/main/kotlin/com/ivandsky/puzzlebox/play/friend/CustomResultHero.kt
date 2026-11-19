package com.ivandsky.puzzlebox.play.friend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.play.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultCommunity
import com.ivandsky.puzzlebox.gameui.ResultHeroChip
import com.ivandsky.puzzlebox.resultui.ResultBelowCardRows
import com.ivandsky.puzzlebox.resultui.ResultHeroActions
import com.ivandsky.puzzlebox.resultui.ResultHeroFacts
import com.ivandsky.puzzlebox.resultui.ResultHeroScreen
import com.ivandsky.puzzlebox.resultui.ResultPrimaryAction
import com.ivandsky.puzzlebox.resultui.replay.ReplaySection

/**
 * A community puzzle's result as the daily's hero page (Result hero polish): the same hero card, chip row and action row,
 * and on a wide screen the two-pane layout, with the puzzle's ranks list ([CustomBoard]) as the side card's body. On a
 * phone the ranks sit under the hero. Daily-only parts (streak, spread, moments, Challenge) have no facts here and draw nothing.
 */
@Composable
internal fun CustomResultHeroScreen(
    state: CustomResultState,
    onDone: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    replayUi: GamePlayUi? = null,
    onOpenReplay: () -> Unit = {},
) {
    val facts = remember(state) { state.heroFacts() }
    ResultHeroScreen(
        facts = facts,
        modifier = modifier,
        brand = state.brand,
        actions = {
            ResultHeroActions(
                primary = ResultPrimaryAction.Done,
                onPrimary = onDone,
                onShare = onShare,
                onPlayAgain = onPlayAgain,
            )
        },
        side = {
            CustomBoard(
                board = state.board,
                author = state.puzzle?.authorPin(),
                yours = state.yours(),
                postState = state.puzzle?.postState,
                rankOrder = state.rankOrder,
                signIn = state.signIn,
                onSignIn = onSignIn,
                closed = state.boardClosed,
                framed = false,
            )
        },
        replay = replayUi?.let { ui ->
            { columnGap ->
                ResultBelowCardRows(
                    post = null,
                    time = null,
                    onPost = {},
                    columnGap = columnGap,
                    replay = { hairline -> ReplaySection(state.replay, ui, onOpen = onOpenReplay, hairline = hairline) },
                )
            }
        },
    )
}

/**
 * The hero's facts for this community puzzle. The header's second line is "by <author>"; the compare is the daily's, over
 * this device's earlier solves of the puzzle, so a first solve has none; a solve that left the record standing is a
 * play again, worded Practice. There is no day, streak or game-wide rank, so those facts are absent.
 */
internal fun CustomResultState.heroFacts(): ResultHeroFacts = ResultHeroFacts(
    timeMs = timeMs,
    hintsUsed = hintsUsed,
    verdict = when (standing) {
        CustomStanding.Record -> RecordVerdict.NewBest
        is CustomStanding.PlayAgain -> RecordVerdict.PlayAgain
        null -> null
    },
    previousBest = (standing as? CustomStanding.PlayAgain)?.record,
    rankOrder = rankOrder,
    game = title,
    puzzleNumber = null,
    practice = false,
    playAgain = standing is CustomStanding.PlayAgain,
    dateLine = puzzle?.let { "by ${it.authorName.trim().ifEmpty { "a friend" }}" }.orEmpty(),
    doneToday = null,
    total = null,
    insights = null,
    rank = ResultRank.Hidden,
    primary = ResultPrimaryAction.Done,
    moments = emptyList(),
    challenge = null,
    samePuzzle = samePuzzle,
    community = ResultCommunity(listOfNotNull(rankChip(), authorChip())),
)

/** This device's best on the puzzle: this solve until a play again names the record that stands. */
internal fun CustomResultState.yours(): BestScore = when (val standing = standing) {
    is CustomStanding.PlayAgain -> standing.record
    CustomStanding.Record, null -> BestScore(timeMs, hintsUsed)
}

/** "#1" over "of 8": this player's place on the puzzle's ranks; null until the board lists them. */
private fun CustomResultState.rankChip(): ResultHeroChip? {
    val ranked = board as? CustomBoardView.Ranked ?: return null
    val mine = (ranked.top + listOfNotNull(ranked.mine)).firstOrNull { it.isMe } ?: return null
    return ResultHeroChip("result.hero.chip.rank", "#${mine.rank}", "of ${ranked.count}")
}

/** The author's time, when the puzzle has one. */
private fun CustomResultState.authorChip(): ResultHeroChip? =
    puzzle?.takeIf { it.authorTimeMs > 0 }?.let { ResultHeroChip("result.hero.chip.author", formatDuration(it.authorTimeMs), "Author") }
