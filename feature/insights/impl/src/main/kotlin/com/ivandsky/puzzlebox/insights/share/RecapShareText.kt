package com.ivandsky.puzzlebox.insights.share

/**
 * The words that travel with the share image: the recap's title, the player's headline number and the link to get
 * the app. Only the player's own figures (A9), so a chat that drops the image still has the message.
 */
internal fun RecapShareContent.shareText(link: String = RecapShareLink): String = buildString {
    append("My PuzzleBox ").append(title)
    hero?.let { append(": ").append(it.value).append(' ').append(it.label) }
    append(". Get PuzzleBox: ").append(link)
}
