package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan.Card

/** What a long press on a card announces. */
internal const val CARD_ACTIONS_LABEL = "Puzzle actions"

/**
 * The long-press menu of a plan [card]: Report for a community or a friend's puzzle, and Remove friend as well for a
 * friend's. Your own puzzle has no menu. [content] draws the card and gets what its long press runs, or null when
 * there is no menu to open.
 */
@Composable
internal fun PlanCardMenu(
    card: Card,
    actions: ShelvesActions,
    content: @Composable (onLongClick: (() -> Unit)?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        content(if (card.origin == Card.Origin.You) null else ({ open = true }))
        AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CardMenuItem("Report", "shelves.card.${card.code.value}.report") {
                open = false
                actions.onReport(card.code)
            }
            if (card.origin == Card.Origin.Friend) {
                CardMenuItem("Remove friend", "shelves.card.${card.code.value}.removeFriend") {
                    open = false
                    actions.onRemoveFriend(card.code)
                }
            }
        }
    }
}

@Composable
private fun CardMenuItem(label: String, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = MaterialTheme.colorScheme.error) },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}
