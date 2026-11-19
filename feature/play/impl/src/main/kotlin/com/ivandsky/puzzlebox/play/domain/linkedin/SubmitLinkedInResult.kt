package com.ivandsky.puzzlebox.play.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPosts
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * Posts the day's result to LinkedIn and returns how that ended. The post does not belong to the caller:
 * cancelling it (the result page was left) stops the wait for the answer, never the post.
 */
class SubmitLinkedInResult(private val posts: LinkedInPosts) {

    suspend operator fun invoke(game: GameId, day: LocalDate): LinkedInPostState = posts.submit(game, day)
}
