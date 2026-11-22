package com.ivandsky.puzzlebox.play.domain.shelves

import com.ivandsky.puzzlebox.model.GameId

private const val SPOTLIGHT_SIZE = 1
private const val MOST_PLAYED_MAX = 6
private const val NEWEST_MAX = 6
private const val SHELF_MIN = 3
private const val NEW_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L

/**
 * The community home's shelves for the loaded public [puzzles], as of [nowMs]. Game shelves follow [gameOrder]
 * (see [gameShelfOrder]); a game not in it gets its shelf after the listed ones.
 *
 * - R1 a puzzle appears at most once; a code listed twice counts once.
 * - R2 fill order: Spotlight (1), Most played (up to 6), Newest (up to 6), game shelves (the leftovers).
 * - R3 a Most played or Newest shelf left with fewer than 3 hides, and its puzzles go on to the next stage.
 * - R4 leftovers after Newest: 3 or more go to the game shelves; 1 or 2 join Most played when it shows (else the game
 *   shelves); none, or only those appended, give one [ShelfPlan.Section.MakeOne] row instead of game shelves.
 * - R5 each game shelf holds only the leftovers of its game, most played first (newest first without a counter).
 * - A3 when no puzzle has a solve count there is no Spotlight and no Most played; a counter on some puzzles counts the
 *   others as 0 solves.
 */
fun buildShelfPlan(puzzles: List<ShelfPuzzle>, gameOrder: List<GameId>, nowMs: Long): ShelfPlan {
    val loaded = puzzles.distinctBy { it.code }
    val hasCounter = loaded.any { it.solves != null }
    val mostPlayedFirst = loaded.sortedWith(
        compareByDescending<ShelfPuzzle> { it.solves ?: 0 }
            .thenByDescending { it.createdAtMs }
            .thenBy { it.code.value },
    )
    fun ShelfPuzzle.card() = ShelfPlan.Card(
        code = code,
        game = game,
        solves = solves,
        isNew = nowMs - createdAtMs < NEW_WINDOW_MS,
        origin = when {
            isOwn -> ShelfPlan.Card.Origin.You
            isFriend -> ShelfPlan.Card.Origin.Friend
            else -> ShelfPlan.Card.Origin.Community
        },
    )

    // `pool` is what no section above has taken yet, most played first.
    var pool = mostPlayedFirst
    val sections = mutableListOf<ShelfPlan.Section>()

    if (hasCounter && pool.size >= SPOTLIGHT_SIZE) {
        sections += ShelfPlan.Section.Spotlight(pool.first().card())
        pool = pool.drop(SPOTLIGHT_SIZE)
    }

    var mostPlayedAt = -1
    var mostPlayed = emptyList<ShelfPuzzle>()
    if (hasCounter) {
        val taken = pool.take(MOST_PLAYED_MAX)
        if (taken.size >= SHELF_MIN) {
            mostPlayedAt = sections.size
            mostPlayed = taken
            sections += ShelfPlan.Section.MostPlayed(emptyList())
            pool = pool.drop(taken.size)
        }
    }

    val newest = pool.sortedWith(compareByDescending<ShelfPuzzle> { it.createdAtMs }.thenBy { it.code.value }).take(NEWEST_MAX)
    if (newest.size >= SHELF_MIN) {
        sections += ShelfPlan.Section.Newest(newest.map { it.card() })
        pool = pool - newest.toSet()
    }

    if (mostPlayedAt >= 0 && pool.size < SHELF_MIN) {
        mostPlayed = mostPlayed + pool
        pool = emptyList()
    }
    if (mostPlayedAt >= 0) sections[mostPlayedAt] = ShelfPlan.Section.MostPlayed(mostPlayed.map { it.card() })

    if (pool.isEmpty()) {
        sections += ShelfPlan.Section.MakeOne(gameOrder)
    } else {
        val games = gameOrder.distinct() + (loaded.map { it.game }.distinct() - gameOrder.toSet())
        for (game in games) {
            sections += ShelfPlan.Section.GameShelf(
                game = game,
                cards = pool.filter { it.game == game }.map { it.card() },
                inAll = loaded.count { it.game == game },
            )
        }
    }
    return ShelfPlan(sections)
}
