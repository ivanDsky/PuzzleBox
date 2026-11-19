package com.ivandsky.puzzlebox.insights.share

/**
 * What the recap's share page shows: the period's [title] and the player's [displayName] for the image, whether the
 * history is still [restoring] (A23: Share is off then, as History is), whether a share is being prepared
 * ([sharing]) and whether the last attempt could not write the image ([failed]).
 */
internal data class RecapShareState(
    val title: String,
    val displayName: String?,
    val restoring: Boolean,
    val sharing: Boolean,
    val failed: Boolean,
) {

    /** Share does something only when the history is whole and no share is already on its way. */
    val canShare: Boolean get() = !restoring && !sharing
}
