package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant

/** One variant of a technique with a demo to watch: what the variant says ([variant]) and its recorded [demo]. */
internal class IntroEntry(val variant: IntroVariant, val demo: IntroDemo) {
    /** The demo's frame count. */
    val frames: Int get() = demo.captions.size
}

/**
 * A technique's intro (spec §2.7): the [entries], one per variant that has a demo on the chapter's drill puzzles, in
 * variant order (never empty), and the [first] puzzle, the one the first entry was recorded on, which is board 1 of
 * the chapter so the board opens on the position the player has just watched.
 */
internal class TechniqueIntro(val entries: List<IntroEntry>, val first: Puzzle)
