package com.ivandsky.puzzlebox.create.published

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.create.editor.CodeCopiedToast
import com.ivandsky.puzzlebox.create.editor.MissingDraftScreen
import com.ivandsky.puzzlebox.create.editor.PublishedTicket
import com.ivandsky.puzzlebox.create.editor.ReadOnlyBoard
import com.ivandsky.puzzlebox.create.editor.ShareTicket
import com.ivandsky.puzzlebox.create.editor.SidePaneWidth
import com.ivandsky.puzzlebox.create.editor.SquareBoardSlot
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.maxContentWidth
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A published puzzle's page (spec A6): under a compact bar whose Back ([onBack]) returns to the
 * Create root, the board as published (read-only), the game's own publish details as published
 * (disabled; Strands' theme clue), the share ticket with Copy code and Share, a
 * note that it can't be edited, **Duplicate as new draft**, which stores an editable copy and
 * opens it through [onOpen], and, while the code is live, **Unpublish…** behind its confirm
 * dialog; once unpublished the ticket strikes its code and the action collapses (**Shrink +
 * Fade**, DurationMedium). Copy's "Code copied" and the "Unpublished" toasts rise at the
 * bottom. The page **Cross-fades** in once loaded (DurationMedium; ReducedFadeDuration under
 * reduced motion). A code with no stored row or kept draft shows the missing-draft screen.
 * [PlayLayout.TwoPane] sets the board square on the left and the ticket and actions in the
 * Studio's side pane on the right, so a tablet shows both without scrolling.
 */
@Composable
internal fun PublishedPuzzleRoute(
    key: PublishedPuzzleKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onOpen: (draftId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PublishedPuzzleViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reduced = rememberReducedMotion()
    val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
    AnimatedContent(
        targetState = state,
        modifier = modifier.testTag("route.publishedPuzzle"),
        transitionSpec = { ContentTransform(fadeIn(tween(duration)), fadeOut(tween(duration)), sizeTransform = null) },
        contentKey = { it::class },
        label = "publishedPage",
    ) { shown ->
        when (shown) {
            PublishedPuzzleViewModel.State.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            PublishedPuzzleViewModel.State.Missing -> MissingDraftScreen(onBack = onBack)
            is PublishedPuzzleViewModel.State.Ready -> PublishedPage(
                page = shown,
                unpublish = viewModel.unpublish,
                layout = layout,
                onBack = onBack,
                onDuplicate = { viewModel.duplicate(onOpen) },
                onConfirmUnpublish = viewModel::confirmUnpublish,
            )
        }
    }
}

@Composable
private fun PublishedPage(
    page: PublishedPuzzleViewModel.State.Ready,
    unpublish: UnpublishSession,
    layout: PlayLayout,
    onBack: () -> Unit,
    onDuplicate: () -> Unit,
    onConfirmUnpublish: () -> Unit,
) {
    var copies by remember { mutableIntStateOf(0) }
    val asked by unpublish.state.collectAsStateWithLifecycle()
    val board: @Composable (Modifier) -> Unit = { boardModifier ->
        ReadOnlyBoard(board = { page.ui.Board(page.controller, page.verdict, layout, it) }, modifier = boardModifier)
    }
    val actions: @Composable ColumnScope.() -> Unit = {
        page.ui.PublishFields(page.controller, enabled = false, modifier = Modifier.fillMaxWidth())
        PublishedActions(page.ticket, onCopied = { copies++ }, onDuplicate = onDuplicate, onUnpublish = unpublish::ask)
    }
    val toasts: @Composable BoxScope.() -> Unit = {
        CodeCopiedToast(
            code = page.ticket.code,
            copies = copies,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl),
        )
        UnpublishedToast(
            code = page.ticket.code,
            done = asked.done,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl),
        )
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CompactTopBar(
            title = page.ticket.title,
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("create.publishedPage.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back to Create")
                }
            },
        )
        when (layout) {
            PlayLayout.SinglePane -> Box(Modifier.weight(1f).fillMaxWidth()) {
                PageColumn(Modifier.maxContentWidth()) {
                    board(Modifier.fillMaxWidth())
                    actions()
                }
                toasts()
            }
            PlayLayout.TwoPane -> Row(Modifier.weight(1f).fillMaxWidth()) {
                SquareBoardSlot(
                    alignment = Alignment.Center,
                    board = board,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(Spacing.lg),
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.width(SidePaneWidth).fillMaxHeight().testTag("create.publishedPage.sidePane")) {
                    PageColumn(Modifier.fillMaxWidth(), content = actions)
                    toasts()
                }
            }
        }
    }
    UnpublishDialog(
        title = page.ticket.title,
        code = page.ticket.code,
        state = asked,
        onConfirm = onConfirmUnpublish,
        onCancel = unpublish::cancel,
    )
}

/** The page's scrolling column, [width] wide, at the page's margins and rhythm. */
@Composable
private fun PageColumn(width: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(width)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        content = content,
    )
}

/**
 * The share [ticket] (Copy reports through [onCopied]), the can't-edit note, Duplicate as new
 * draft ([onDuplicate]) and, while the code is live, Unpublish… ([onUnpublish]), which collapses
 * once unpublished.
 */
@Composable
private fun ColumnScope.PublishedActions(ticket: ShareTicket, onCopied: () -> Unit, onDuplicate: () -> Unit, onUnpublish: () -> Unit) {
    val duration = if (rememberReducedMotion()) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
    PublishedTicket(ticket, onCopied = onCopied, modifier = Modifier.fillMaxWidth())
    Text(
        text = if (ticket.unpublished) {
            "Unpublished puzzles stay here as a record. Duplicate it to share it again; that publishes under a new code."
        } else {
            "Published puzzles can’t be edited. Duplicate it to make changes; that publishes under a new code."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        overflow = TextOverflow.Ellipsis,
    )
    SecondaryButton(
        text = "Duplicate as new draft",
        onClick = onDuplicate,
        modifier = Modifier.fillMaxWidth().testTag("create.publishedPage.duplicate"),
    )
    AnimatedVisibility(
        visible = !ticket.unpublished,
        enter = fadeIn(tween(duration)),
        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
    ) {
        TextButton(
            onClick = onUnpublish,
            modifier = Modifier.fillMaxWidth().testTag("create.publishedPage.unpublish"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Unpublish…")
        }
    }
}
