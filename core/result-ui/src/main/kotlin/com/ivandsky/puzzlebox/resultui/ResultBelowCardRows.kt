package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.gameui.LinkedInPostRowState

/**
 * The rows under the result card, for [ResultHeroScreen]'s `replay` slot: the Post to LinkedIn row for [post] (null
 * draws none), and the Watch replay row from [replay], told whether to draw its own hairline.
 *
 * On a two-column page ([columnGap] given) the rows share one line across both cards: the post row under the hero
 * card, Watch replay under the side card, each with its column's width, so even the longest status has room. On one
 * column (null) they stack, post above replay, each at full width. Either way the layout depends only on the page,
 * never on the post row's status, which changes while the screen is up (posting, posted, check), so the rows never jump.
 * With a post row one hairline runs over the group; without one the replay row is drawn as it always was, under the
 * hero card with its own hairline.
 */
@Composable
fun ResultBelowCardRows(
    post: LinkedInPostRowState?,
    time: String?,
    onPost: () -> Unit,
    replay: (@Composable (hairline: Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
    columnGap: Dp? = null,
) {
    if (post == null) {
        Column(modifier) {
            if (columnGap == null) {
                replay?.invoke(true)
            } else {
                UnderResultColumns(columnGap, hero = { replay?.invoke(true) }, side = null)
            }
        }
        return
    }
    val postRow = @Composable { LinkedInPostRow(post, time, onPost, pending = pending) }
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (columnGap == null) {
            postRow()
            replay?.invoke(false)
        } else {
            UnderResultColumns(columnGap, hero = postRow, side = replay?.let { { it(false) } })
        }
    }
}
