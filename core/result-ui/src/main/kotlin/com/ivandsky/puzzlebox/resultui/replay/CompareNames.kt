package com.ivandsky.puzzlebox.resultui.replay

/**
 * What a compare calls its two sides; the caller picks the words ("You", a friend's name). [theirsPast] marks the right
 * side as the viewer's own past solve: its header wears the Past you avatar (the viewer's initial with the history mark),
 * drawn with the viewer's own photo at [theirsPastPhotoUrl] when the caller has one.
 */
class CompareNames(val mine: String, val theirs: String, val theirsPast: Boolean = false, val theirsPastPhotoUrl: String? = null)
