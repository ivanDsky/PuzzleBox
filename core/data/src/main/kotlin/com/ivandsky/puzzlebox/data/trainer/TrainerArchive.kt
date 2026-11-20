package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId

/**
 * The archive puzzles a Trainer round drills a technique on, fetched one at a time on the player's tap into the
 * trainer's own cache (`trainer_puzzles`), never into a daily cache: History, the archive month and Random read
 * only the daily cache, so a puzzle the player only trained on never shows there.
 *
 * The caller names the candidate numbers (the technique index, most target steps first); this never picks one.
 * Nothing is fetched ahead of a tap and one [load] is at most one fetch.
 */
interface TrainerArchive {

    /**
     * The [numbers] whose puzzle is already in the trainer cache and confirmed to use [technique], in the order
     * given and without any the walker rejected this session. Never fetches, so it is safe to ask while offline.
     */
    suspend fun onDevice(game: GameId, technique: TechniqueId, numbers: List<Int>): List<Int>

    /**
     * One tap: [number]'s puzzle from the trainer cache, or else exactly one fetch of it through the game's own
     * daily source under the app's LinkedIn call budget. A trusted fetch is stored before the walker looks at
     * it, so the content stays on the device whatever the result. Never waits for budget and never fetches a
     * second number.
     */
    suspend fun load(game: GameId, technique: TechniqueId, number: Int): ArchiveLoad
}
