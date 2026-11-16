package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.editor.Difficulty

/**
 * What [SharedPuzzleRepository.listPublic] narrows the community list to. A null [gameId] is every
 * game. An empty [difficulties] is every puzzle, labelled or not; otherwise only puzzles labelled
 * with one of these levels are listed, so a puzzle with no difficulty is left out. The Hard chip
 * asks for Hard and Expert.
 */
data class CommunityFilter(
    val gameId: String? = null,
    val difficulties: Set<Difficulty.Level> = emptySet(),
)
