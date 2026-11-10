package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead

/**
 * Finishes connecting: one `check` learns the member's profile id, then the link stores it
 * with the consent time. The only place a profile id enters the link.
 *
 * Any reply but a profile id is returned unchanged and nothing is written, not even a pause:
 * with no profile there is nothing to pause, so the player stays not connected and can retry.
 * For the same reason this one call is exempt from the call budget and the latch; it is a
 * player action, one per Connect press, never a background path.
 */
class CreateLinkedInLink(
    private val source: LinkedInAccountSource,
    private val link: LinkedInLink,
    private val daily: DailyRepository,
    private val registry: GameRegistry,
    private val clock: () -> Long,
) {

    suspend operator fun invoke(): LinkedInRead<Unit> {
        // Every LinkedIn game's type answers with the member's game URN, so the first one will do.
        val typeId = checkNotNull(
            (registry.all + registry.unlisted).firstNotNullOfOrNull { daily.linkedInGameTypeId(it.definition.id) },
        ) { "no LinkedIn game registered" }
        return when (val reply = source.check(typeId)) {
            is LinkedInRead.Ok -> {
                link.connect(reply.value, clock())
                LinkedInRead.Ok(Unit)
            }
            LinkedInRead.Offline -> LinkedInRead.Offline
            LinkedInRead.SignedOut -> LinkedInRead.SignedOut
            is LinkedInRead.Rejected -> LinkedInRead.Rejected(reply.kind)
        }
    }
}
