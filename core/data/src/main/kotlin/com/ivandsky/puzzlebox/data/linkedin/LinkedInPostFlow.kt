package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostEligibility.Facts
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostEligibility.Verdict
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.database.linkedin.FetchLogDao
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInProgress
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInResultPoster
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/**
 * The post door over the link file and the LinkedIn ports. A submit asks LinkedIn for the player's own result,
 * reads the game page, and only then sends, taking a call slot and asking [LinkedInPostEligibility] again before
 * each of the three requests.
 *
 * A post runs in [scope], the app's, and not in its caller: the request may already be on the wire when the
 * screen that asked is left, and its outcome must still be recorded.
 */
internal class LinkedInPostFlow(
    private val link: LinkedInLink,
    private val source: LinkedInAccountSource,
    private val poster: LinkedInResultPoster,
    private val completions: CompletionRepository,
    private val daily: DailyRepository,
    calendar: GameCalendar,
    private val registry: GameRegistry,
    private val log: LinkedInPostLog,
    private val fetchLog: FetchLogDao,
    private val budget: LinkedInCallBudget,
    private val storeLock: LinkedInStoreLock,
    private val scope: CoroutineScope,
) : LinkedInPosts {

    private val window = LinkedInDayWindow(calendar)

    /**
     * What only this process knows of each day: its running post, which every caller of the day awaits, so the
     * day is sent once however many ask, or the outcome of its last tap when no stored state can tell it.
     */
    private val slots = MutableStateFlow<Map<DayKey, Slot>>(emptyMap())

    override fun observe(game: GameId, day: LocalDate): Flow<LinkedInPostView> {
        val readings = combine(
            link.posting,
            link.state,
            completions.observeRecords(),
            completions.observeImports(),
            log.observe(game, day),
        ) { posting, state, records, imports, entry ->
            reading(game, day, posting, state, records, imports, entry)
        }
        return combine(readings, slots) { reading, slots ->
            LinkedInPostView(shown(reading.verdict, reading.key?.let(slots::get)), reading.timeMs)
        }
            .distinctUntilChanged()
    }

    /**
     * Hidden wins over everything, so posting turned off hides a posted or a sending day. A running post shows
     * next, then a pause, then the last tap's outcome. A day that is over has no button unless a tap was refused.
     */
    private fun shown(verdict: Verdict, slot: Slot?): LinkedInPostState {
        val blocked = (verdict as? Verdict.Blocked)?.state
        return when {
            blocked == LinkedInPostState.Hidden -> blocked
            slot is Slot.Flying -> LinkedInPostState.Sending
            blocked == LinkedInPostState.Paused -> blocked
            slot is Slot.Settled -> slot.outcome
            blocked == LinkedInPostState.NotToday -> LinkedInPostState.Hidden
            blocked != null -> blocked
            (verdict as Verdict.Open).unsure -> LinkedInPostState.Unknown
            else -> LinkedInPostState.Ready
        }
    }

    override suspend fun submit(game: GameId, day: LocalDate): LinkedInPostState {
        val reading = reading(game, day)
        return when (val verdict = reading.verdict) {
            is Verdict.Open -> flight(DayKey(verdict.profileId, game, day)).await()
            is Verdict.Blocked -> {
                // A page left open past the game's midnight still offered this tap, so its button is told why nothing went out.
                if (verdict.state == LinkedInPostState.NotToday && reading.key != null) {
                    slots.update { if (it[reading.key] is Slot.Flying) it else it + (reading.key to Slot.Settled(verdict.state)) }
                }
                verdict.state
            }
        }
    }

    /**
     * The day's running post, started when there is none. Unlike a day sync, a post is never handed to a waiter
     * when its first caller is cancelled: it does not belong to a caller at all, so none can end it or start a second.
     */
    private fun flight(key: DayKey): Deferred<LinkedInPostState> {
        val fresh = scope.async(start = CoroutineStart.LAZY) {
            var outcome: LinkedInPostState? = null
            try {
                post(key).also { outcome = it }
            } finally {
                // Released and told in one step, so the button never shows Ready between Sending and the outcome.
                val told = outcome?.takeIf { it in UNSTORED }
                slots.update { if (told == null) it - key else it + (key to Slot.Settled(told)) }
            }
        }
        val slot = slots.updateAndGet { if (it[key] is Slot.Flying) it else it + (key to Slot.Flying(fresh)) }.getValue(key) as Slot.Flying
        if (slot.post === fresh) fresh.start() else fresh.cancel()
        return slot.post
    }

    private suspend fun post(key: DayKey): LinkedInPostState {
        val checked = open(key) { return it }
        when (val own = source.ownResult(key.profileId, checked.gameTypeId, checked.puzzleNumber)) {
            is LinkedInRead.Ok -> (own.value as? LinkedInOwnResult.Solved)?.let { held ->
                return whileLinked(key) { alreadyThere(key, posted = landed(key, checked, held)) }
            }
            else -> return unread(key, own)
        }
        val asked = open(key) { return it }
        val page = when (val read = poster.gamePage(asked)) {
            is LinkedInRead.Ok -> read.value
            else -> return unread(key, read)
        }
        when {
            // A page of another member means the session is not the linked profile's: nothing may be sent as it.
            page.profileId != key.profileId -> return pause(key, Kind.Unexpected)
            // Before the puzzle id: a tutorial carries its own id, and LinkedIn still onboarding the account is no fault of the link.
            page.isTutorial -> return LinkedInPostState.Tutorial
            page.puzzleId != asked.puzzleNumber.toLong() -> return LinkedInPostState.NotToday
            page.progress == LinkedInProgress.Won -> return whileLinked(key) { alreadyThere(key, posted = false) }
        }
        val post = open(key) { return it }
        // Under the lock a disconnect holds for its whole wipe: a post that got here mid-wipe waits, then finds no link to mark.
        if (!storeLock.withLock { log.markSending(key) }) return LinkedInPostState.Hidden
        val sent = poster.send(post, page)
        return whileLinked(key) { answered(key, sent) }
    }

    private suspend fun answered(key: DayKey, sent: LinkedInSendResult): LinkedInPostState = when {
        // The marker goes with the link it was written under, so without it the answer belongs to a link that is gone.
        !log.observe(key.game, key.day).first().sending -> LinkedInPostState.Hidden
        sent == LinkedInSendResult.Posted -> alreadyThere(key, posted = true)
        sent == LinkedInSendResult.AlreadyPlayed -> alreadyThere(key, posted = false, remember = true)
        // Nothing is known, so the marker stays: the day reads as unsure until a later check settles it.
        sent == LinkedInSendResult.Unknown -> LinkedInPostState.Unknown
        else -> {
            log.settle(key, posted = false)
            when (sent) {
                is LinkedInSendResult.Rejected -> paused(sent.kind)
                LinkedInSendResult.SignedOut -> paused(Kind.SessionExpired)
                else -> LinkedInPostState.Offline
            }
        }
    }

    /**
     * Runs [store] only while [key]'s profile is still the linked one, under the lock a disconnect holds for its
     * whole wipe. An outcome that lands after its link is gone is dropped: it must not leak into what is linked by then.
     */
    private suspend fun whileLinked(key: DayKey, store: suspend () -> LinkedInPostState): LinkedInPostState =
        storeLock.withLock { if (link.state.first().profileId == key.profileId) store() else LinkedInPostState.Hidden }

    /**
     * Takes a call slot, then asks the rule again, since the wait for a slot or the request before it may have
     * outlived the consent, the link or the day. Leaves through [blocked] unless [key]'s profile may still post.
     */
    private suspend inline fun open(key: DayKey, blocked: (LinkedInPostState) -> Nothing): LinkedInPost {
        budget.acquire()
        return when (val verdict = verdict(key.game, key.day)) {
            is Verdict.Blocked -> blocked(verdict.state)
            is Verdict.Open -> if (verdict.profileId == key.profileId) verdict.post else blocked(LinkedInPostState.Hidden)
        }
    }

    /** A check or a page read that brought no answer. It writes nothing to the post log, so an unsure day stays unsure. */
    private suspend fun unread(key: DayKey, read: LinkedInRead<*>): LinkedInPostState = when (read) {
        is LinkedInRead.Ok -> error("an answered read is not a failure")
        LinkedInRead.Offline -> LinkedInPostState.Offline
        // Nothing was sent, but the session is gone all the same, so the player is asked to reconnect.
        LinkedInRead.SignedOut -> pause(key, Kind.SessionExpired)
        is LinkedInRead.Rejected -> pause(key, read.kind)
    }

    /**
     * LinkedIn holds a result for the day. The marker goes, and the day card's cached own read with it, so the card
     * reads the result on its next sync. Only a result this phone sent is remembered as [posted]. A [remember]ed
     * answer of the send itself keeps reading as already there after a restart; a check's does not, as before.
     */
    private suspend fun alreadyThere(key: DayKey, posted: Boolean, remember: Boolean = false): LinkedInPostState {
        log.settle(key, posted, already = remember)
        fetchLog.deleteOwnEntry(key.game.value, key.day.toString(), key.profileId)
        return if (posted) LinkedInPostState.Posted else LinkedInPostState.AlreadyOnLinkedIn
    }

    /**
     * Whether the result LinkedIn holds is this phone's own post that landed unanswered: the day was left sending,
     * and the result's time is the post's in whole seconds, the unit LinkedIn's read reports.
     */
    private suspend fun landed(key: DayKey, post: LinkedInPost, held: LinkedInOwnResult.Solved): Boolean =
        log.observe(key.game, key.day).first().sending && held.timeSeconds.toLong() == post.timeMs / 1000

    private suspend fun pause(key: DayKey, kind: Kind): LinkedInPostState = whileLinked(key) { paused(kind) }

    private suspend fun paused(kind: Kind): LinkedInPostState {
        link.pause(kind)
        return LinkedInPostState.Paused
    }

    private suspend fun reading(game: GameId, day: LocalDate): Reading =
        reading(
            game,
            day,
            link.posting.first(),
            link.state.first(),
            completions.observeRecords().first(),
            completions.observeImports().first(),
            log.observe(game, day).first(),
        )

    private suspend fun verdict(game: GameId, day: LocalDate): Verdict = reading(game, day).verdict

    private suspend fun reading(
        game: GameId,
        day: LocalDate,
        posting: Boolean,
        state: LinkState,
        records: List<CompletionRecord>,
        imports: List<CompletionRecord>,
        entry: LinkedInPostLog.Entry,
    ): Reading {
        val facts = Facts(
            posting = posting,
            link = state,
            record = records.firstOrNull { it.isOf(game, day) },
            onLinkedIn = imports.any { it.isOf(game, day) },
            typeId = daily.linkedInGameTypeId(game),
            gamePath = daily.linkedInGamePath(game),
            delta = window.deltaOf(game, day),
            posted = entry.posted,
            sending = entry.sending,
            already = entry.already,
        )
        return Reading(
            key = state.profileId?.let { DayKey(it, game, day) },
            timeMs = facts.record?.time,
            verdict = LinkedInPostEligibility.decide(facts) { facts.record?.let { play(game, day, it) } },
        )
    }

    /** Null when the solve's replay or the day's puzzle is no longer stored, or the game does not encode it. */
    private suspend fun play(game: GameId, day: LocalDate, record: CompletionRecord): LinkedInPlay? {
        val replay = completions.recordReplay(record.puzzleRef) ?: return null
        val puzzle = daily.cachedPuzzle(game, day) ?: return null
        return registry[game]?.linkedInPlayOf(puzzle, replay)
    }

    /** One game day as the rule sees it, its key under the linked profile (null while not linked) and the time of its record row. */
    private class Reading(val key: DayKey?, val timeMs: Long?, val verdict: Verdict)

    private sealed interface Slot {
        class Flying(val post: Deferred<LinkedInPostState>) : Slot

        class Settled(val outcome: LinkedInPostState) : Slot
    }

    private companion object {
        /** The outcomes no stored state can tell: shown until the day's next tap or the end of the process. */
        val UNSTORED = setOf(LinkedInPostState.Offline, LinkedInPostState.AlreadyOnLinkedIn, LinkedInPostState.NotToday, LinkedInPostState.Tutorial)
    }
}

private val LinkState.profileId: String?
    get() = when (this) {
        LinkState.NotConnected -> null
        is LinkState.Connected -> profileId
        is LinkState.Paused -> profileId
    }

private fun CompletionRecord.isOf(game: GameId, day: LocalDate): Boolean =
    puzzleRef.gameId == game && (puzzleRef.key as? PuzzleKey.Daily)?.date == day
