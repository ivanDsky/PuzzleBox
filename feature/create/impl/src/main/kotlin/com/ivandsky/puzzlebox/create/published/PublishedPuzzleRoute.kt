package com.ivandsky.puzzlebox.create.published

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.maxContentWidth
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A published puzzle's page (spec A6): under a compact bar whose Back ([onBack]) returns to the
 * Create root, the board as published (read-only), the share ticket with Copy code and Share, a
 * note that it can't be edited, and **Duplicate as new draft**, which stores an editable copy and
 * opens it through [onOpen]. Copy's "Code copied" toast rises at the bottom. The page
 * **Cross-fades** in once loaded (DurationMedium; ReducedFadeDuration under reduced motion).
 * A code with no stored row or kept draft shows the missing-draft screen.
 */
@Composable
internal fun PublishedPuzzleRoute(
    key: PublishedPuzzleKey,
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
            is PublishedPuzzleViewModel.State.Ready -> PublishedPage(shown, onBack, onDuplicate = { viewModel.duplicate(onOpen) })
        }
    }
}

@Composable
private fun PublishedPage(page: PublishedPuzzleViewModel.State.Ready, onBack: () -> Unit, onDuplicate: () -> Unit) {
    var copies by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            CompactTopBar(
                title = page.ticket.title,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("create.publishedPage.back")) {
                        Icon(PuzzleIcons.Back, contentDescription = "Back to Create")
                    }
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .maxContentWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                ReadOnlyBoard(
                    board = { boardModifier -> page.ui.Board(page.controller, page.verdict, boardModifier) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PublishedTicket(page.ticket, onCopied = { copies++ }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Published puzzles can’t be edited. Duplicate it to make changes; that publishes under a new code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                )
                SecondaryButton(
                    text = "Duplicate as new draft",
                    onClick = onDuplicate,
                    modifier = Modifier.fillMaxWidth().testTag("create.publishedPage.duplicate"),
                )
            }
        }
        CodeCopiedToast(
            code = page.ticket.code,
            copies = copies,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl),
        )
    }
}
