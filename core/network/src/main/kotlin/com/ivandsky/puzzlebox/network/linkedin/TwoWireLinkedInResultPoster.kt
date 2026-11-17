package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInResultPoster
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult

/**
 * The post port over LinkedIn's two write wires (ADR-0032 §11). The play picks the wire: one that carries a
 * voyager board is read and saved over [voyager], every other over [sdui]. The game supplies that play, so
 * nothing here knows which game it is, and a game LinkedIn moves to `updateGameState` changes its encoder only.
 */
internal class TwoWireLinkedInResultPoster(
    private val sdui: LinkedInResultPoster,
    private val voyager: VoyagerGameSave,
) : LinkedInResultPoster {

    override suspend fun gamePage(gamePath: String): LinkedInRead<LinkedInGamePage> = sdui.gamePage(gamePath)

    override suspend fun gamePage(post: LinkedInPost): LinkedInRead<LinkedInGamePage> =
        if (post.play.voyager != null) voyager.game(post) else sdui.gamePage(post)

    override suspend fun send(post: LinkedInPost, page: LinkedInGamePage): LinkedInSendResult {
        val play = post.play.voyager ?: return sdui.send(post, page)
        return voyager.send(post, play, page)
    }
}
