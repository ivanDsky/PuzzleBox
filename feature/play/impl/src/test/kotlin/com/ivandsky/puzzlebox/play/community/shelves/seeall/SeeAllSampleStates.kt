package com.ivandsky.puzzlebox.play.community.shelves.seeall

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.CommunityRowModel
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.community.GameChoice
import com.ivandsky.puzzlebox.play.community.shelves.codeOf

/** Rows and states the See all screen tests and goldens draw. */
internal object SeeAllSampleStates {
    val games = listOf(GameChoice(ALPHA, "Alpha"), GameChoice(BETA, "Beta"))

    fun row(n: Int, game: String = "Alpha", title: String = "Puzzle $n", author: String = "Author $n") = CommunityRowModel(
        code = codeOf(n),
        game = game,
        brand = GameBrand.Neutral,
        title = title,
        author = author,
        difficulty = Difficulty.Level.Medium,
        age = "${n}h ago",
    )

    fun ready(
        scope: SeeAllScope = SeeAllScope.Newest,
        count: Int = 6,
        query: String = "",
        difficulty: DifficultyChoice? = null,
        game: com.ivandsky.puzzlebox.model.GameId? = null,
    ) = SeeAllUiState(
        scope = scope,
        phase = SeeAllPhase.Ready((1..count).map { row(it) }),
        query = query,
        difficulty = difficulty,
        game = game,
        games = games,
        showGameFilter = scope !is SeeAllScope.Game,
    )

    fun noMatch(query: String = "zebra") = SeeAllUiState(
        scope = SeeAllScope.Search,
        phase = SeeAllPhase.Ready(emptyList()),
        query = query,
        games = games,
        noMatch = true,
    )
}
