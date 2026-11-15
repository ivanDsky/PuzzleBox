package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPosts
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** What the day's Post to LinkedIn button says, a running post and its outcome included. It never asks LinkedIn. */
class ObserveLinkedInPost(private val posts: LinkedInPosts) {

    operator fun invoke(game: GameId, day: LocalDate): Flow<LinkedInPostState> = posts.observe(game, day)
}
