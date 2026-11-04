package com.ivandsky.puzzlebox.games.beta

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.alpha.AlphaPreviews

@AlphaPreviews
@Composable
private fun ViaMultipreviewPreview() {
    ViaMultipreview()
}

@AlphaPreviews
@Composable
private fun CommentOnlyPreview() {
    // MentionedInComment()
    /* MentionedInComment() */
}

@AlphaPreviews
@Composable
private fun WordBoundaryPreview() {
    FooBar()
}

@AlphaPreviews
@Composable
private fun ParentPreview() {
    Parent()
}

@AlphaPreviews
@Composable
private fun TrailingLambdaPreview() {
    Wrapper {
    }
}
