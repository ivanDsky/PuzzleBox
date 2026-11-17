package com.ivandsky.puzzlebox.testing.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInResultPoster
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Scriptable [LinkedInResultPoster]. Every call is recorded in [calls] as `"page:<gamePath>"` or `"send"`, and
 * every send in [sent] with the page it carried. An unscripted call answers Offline, the same as the real
 * poster with no network.
 *
 * Thread-safe: the post flow calls it from the app scope while the test scripts it.
 */
class FakeLinkedInResultPoster : LinkedInResultPoster {

    /** The reply to every following [gamePage]. */
    @Volatile
    var pageReply: LinkedInRead<LinkedInGamePage> = LinkedInRead.Offline

    /** The reply to every following [send]. */
    @Volatile
    var sendReply: LinkedInSendResult = LinkedInSendResult.Offline

    /** Runs inside every [send] after it is recorded and before it answers: where a test holds a post on the wire. */
    @Volatile
    var onSend: suspend () -> Unit = {}

    private val recorded = CopyOnWriteArrayList<String>()
    private val posts = CopyOnWriteArrayList<Pair<LinkedInPost, LinkedInGamePage>>()

    /** Every call so far, oldest first. */
    val calls: List<String> get() = recorded.toList()

    /** Every post sent so far with its page, oldest first. */
    val sent: List<Pair<LinkedInPost, LinkedInGamePage>> get() = posts.toList()

    override suspend fun gamePage(post: LinkedInPost): LinkedInRead<LinkedInGamePage> {
        recorded += "page:${post.gamePath}"
        return pageReply
    }

    override suspend fun send(post: LinkedInPost, page: LinkedInGamePage): LinkedInSendResult {
        recorded += "send"
        posts += post to page
        onSend()
        return sendReply
    }
}
