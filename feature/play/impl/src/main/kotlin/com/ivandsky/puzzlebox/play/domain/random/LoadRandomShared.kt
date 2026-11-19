package com.ivandsky.puzzlebox.play.domain.random

import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import kotlin.random.Random

/**
 * One random puzzle from New today ([ListCommunityPuzzles], so own, hidden and aged-out puzzles are already gone).
 * The [last] pick is skipped while another puzzle is listed; a list of exactly that one returns it again.
 */
class LoadRandomShared(
    private val list: ListCommunityPuzzles,
    private val random: Random = Random.Default,
) {
    suspend operator fun invoke(filter: RandomFilter.Community, last: RandomPick?): RandomOutcome =
        when (val result = list(CommunityFilter(filter.game?.value, filter.difficulties))) {
            is CommunityListResult.Listed -> {
                val codes = result.puzzles.map { it.code }
                val lastCode = (last as? RandomPick.Shared)?.code
                val pool = codes.filter { it != lastCode }.ifEmpty { codes }
                if (pool.isEmpty()) {
                    RandomOutcome.NothingLeft
                } else {
                    RandomOutcome.Picked(RandomPick.Shared(pool[random.nextInt(pool.size)]))
                }
            }
            CommunityListResult.Offline -> RandomOutcome.Offline
            CommunityListResult.Refused -> RandomOutcome.Refused
        }
}
