package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * Whether an open [PublishSession] lets the author change the level (96): the doc going out Public, since a link only
 * doc stores no level.
 */
internal class PublishLevel(private val visibility: PublishVisibility) {
    /** The level can be changed here. */
    val offered: Boolean get() = visibility.sent == Visibility.Public

    /** The level the doc carries for [verdict] and the author's kept [pick]; null, the verdict's own label, when not offered. */
    fun carried(verdict: Verdict, pick: Level?): Level? = if (offered) LevelChoice.of(verdict, pick)?.chosen else null
}
