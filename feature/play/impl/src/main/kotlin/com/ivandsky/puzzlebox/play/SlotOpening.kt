package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.PastDayOutcome
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.degradeBannerMessage
import com.ivandsky.puzzlebox.gameui.unavailableMessage
import com.ivandsky.puzzlebox.model.PlaySlot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * Decides where a slot's board comes from (A11), in this order:
 * 1. the slot's saved row, read through the [lane] (no network). It is checked with the
 *    decoders the store restores with: the [SessionBlob] envelope must name the slot's
 *    game and the game's session codec must read it. An undecodable row is deleted and
 *    the slot is built fresh (arch-11);
 * 2. today's daily fetch, when the slot's day is the game's current day;
 * 3. a past day opened by date — from the cache, or fetched once for an archive game —
 *    carrying the day's puzzleNumber from the cache so the record identity (ADR-0004)
 *    survives keys that name only game and day (arch-2);
 * 4. otherwise [Origin.Unavailable], whose copy names the cause.
 */
internal class SlotOpening(
    private val daily: DailyRepository,
    private val pastDays: LoadPastDay,
    private val saved: SavedPlayRepository,
    private val calendar: GameCalendar,
    private val games: GameRegistry,
    private val lane: WriteLane,
) {
    /** Where a slot's board comes from (patterns-9). */
    sealed interface Origin {
        /** A [SessionBlob] envelope to adopt: a saved play or a past day's replay build. */
        class Restored(val session: ByteArray) : Origin

        /** Today's fresh daily; [banner] is the degrade copy when it is a cached fallback;
         *  [puzzleNumber] is the day's number from the cache's history (ADR-0004), if any. */
        class Fetched(val puzzle: Puzzle, val banner: String?, val puzzleNumber: Int?) : Origin

        /** Nothing to play; [message] is the copy. */
        class Unavailable(val message: String) : Origin
    }

    suspend fun open(slot: PlaySlot): Origin {
        val plugin = games[slot.gameId] ?: return Origin.Unavailable(UNREGISTERED)
        restore(slot, plugin)?.let { return Origin.Restored(it) }
        val today = calendar.today(slot.gameId) ?: return Origin.Unavailable(UNREGISTERED)
        return if (slot.day == today) fetch(slot) else build(slot)
    }

    /** The slot's saved envelope when it decodes; an undecodable row is deleted in the same lane turn. */
    private suspend fun restore(slot: PlaySlot, plugin: GamePlugin<*, *, *>): ByteArray? = lane.run {
        val blob = saved.load(slot)
        when {
            blob == null -> null
            decodes(blob, slot, plugin) -> blob
            else -> {
                saved.delete(slot)
                null
            }
        }
    }

    private fun decodes(blob: ByteArray, slot: PlaySlot, plugin: GamePlugin<*, *, *>): Boolean {
        val envelope = SessionBlob.decode(blob) ?: return false
        return envelope.game == slot.gameId && plugin.session.deserialize(envelope.session) is DecodeResult.Ok
    }

    private suspend fun fetch(slot: PlaySlot): Origin = when (val fetched = daily.today(slot.gameId)) {
        is DailyToday.Fresh -> Origin.Fetched(fetched.puzzle, banner = null, puzzleNumberOf(slot))
        is DailyToday.Cached -> Origin.Fetched(fetched.puzzle, degradeBannerMessage(fetched.error), puzzleNumberOf(slot))
        is DailyToday.Unavailable -> Origin.Unavailable(unavailableMessage(fetched.error))
    }

    private suspend fun build(slot: PlaySlot): Origin =
        when (val opened = pastDays(slot.gameId, slot.day, puzzleNumberOf(slot))) {
            is PastDayOutcome.Ready -> Origin.Restored(opened.session)
            PastDayOutcome.Missing -> Origin.Unavailable(DAY_MISSING)
            PastDayOutcome.OutOfRange -> Origin.Unavailable(PAST_DAY_OUT_OF_RANGE)
            is PastDayOutcome.Failed -> Origin.Unavailable(pastDayUnavailableMessage(slot.day, opened.error))
            is PastDayOutcome.Unreadable -> Origin.Unavailable(DAY_UNREADABLE)
        }

    /** The day's puzzleNumber from the cache's history rows (null when the day has none). */
    private suspend fun puzzleNumberOf(slot: PlaySlot): Int? =
        daily.observeHistory(slot.gameId).first().firstOrNull { it.day == slot.day }?.puzzleNumber

    private companion object {
        const val UNREGISTERED = "This game isn't available in this build."
        const val DAY_MISSING = "That day is no longer saved on this device."
        const val DAY_UNREADABLE = "This day didn't load right, so it can't be played again."
    }
}

/** Copy for a day outside the game's archive, or one its source says doesn't exist. */
internal const val PAST_DAY_OUT_OF_RANGE = "This puzzle isn't available."

/** Why an archive day that isn't on the device could not be fetched (SC9). */
internal fun pastDayUnavailableMessage(day: LocalDate, error: FetchError): String = when (error) {
    is FetchError.NotFound -> PAST_DAY_OUT_OF_RANGE
    is FetchError.Unavailable -> "You're offline. ${day.format(PAST_DAY)} needs a connection the first time it opens."
    is FetchError.Untrusted -> "This puzzle couldn't be read. Try again later."
    is FetchError.Throttled -> "Can't load ${day.format(PAST_DAY)} right now. Wait a minute and try again."
}

// The copy is English-only, so the date follows it rather than the device locale.
private val PAST_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)
