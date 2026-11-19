package com.ivandsky.puzzlebox.play.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostView
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPosts
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** What the day's Post to LinkedIn button says and the time it names, a running post and its outcome included. It never asks LinkedIn. */
class ObserveLinkedInPost(private val posts: LinkedInPosts) {

    operator fun invoke(game: GameId, day: LocalDate): Flow<LinkedInPostView> = posts.observe(game, day)
}
