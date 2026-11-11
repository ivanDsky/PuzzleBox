package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
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
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Decides where a slot's board comes from, in this order:
 * 1. the slot's saved row, read through the [lane] (no network). It is checked with the
 *    decoders the store restores with: the [SessionBlob] envelope must name the slot's
 *    game and the game's session codec must read it. An undecodable row is deleted and
 *    the slot is built fresh, so a corrupt save can never block the board;
 * 2. today's daily fetch, when the slot's day is the game's current day;
 * 3. a past day opened by date — from the cache, or fetched once for an archive game —
 *    carrying the day's puzzleNumber from the cache so the record identity (ADR-0004)
 *    survives keys that name only game and day;
 * 4. a friend's puzzle ([PlaySlot.Custom]) from the [received] puzzles stored when it opened;
 * 5. a duel puzzle ([PlaySlot.Leg]) named by its match's leg, built as a replay under the leg
 *    puzzle's own identity: a day through the same past-day path (today's falls back to the
 *    daily fetch before the day is captured), a friend's puzzle through [loadShared], whose
 *    check runs on [DispatcherProvider.default];
 * 6. otherwise [Origin.Unavailable], whose copy names the cause.
 */
internal class SlotOpening(
    private val daily: DailyRepository,
    private val pastDays: LoadPastDay,
    private val saved: SavedPlayRepository,
    private val calendar: GameCalendar,
    private val games: GameRegistry,
    private val lane: WriteLane,
    private val received: ReceivedPuzzleRepository,
    private val matches: MatchRepository,
    private val loadShared: LoadSharedPuzzle,
    private val dispatchers: DispatcherProvider,
) {
    /** Where a slot's board comes from (patterns-9). */
    sealed interface Origin {
        /** A [SessionBlob] envelope to adopt: a saved play or a past day's replay build. */
        class Restored(val session: ByteArray) : Origin

        /** A fresh board: today's daily, or a friend's stored puzzle; [banner] is the degrade copy when
         *  it is a cached fallback; [puzzleNumber] is the day's number from the cache's history (ADR-0004), if any. */
        class Fetched(val puzzle: Puzzle, val banner: String?, val puzzleNumber: Int?) : Origin

        /** Nothing to play; [message] is the copy; [retryable] offers Retry (false when the
         *  source has said the day doesn't exist or can't be trusted — retrying won't help). */
        class Unavailable(val message: String, val retryable: Boolean = true) : Origin
    }

    suspend fun open(slot: PlaySlot): Origin {
        val plugin = games[slot.gameId] ?: return Origin.Unavailable(UNREGISTERED)
        restore(slot, plugin)?.let { return Origin.Restored(it) }
        return when (slot) {
            is PlaySlot.Custom -> friend(slot)
            is PlaySlot.Leg -> leg(slot, plugin)
            is PlaySlot.Daily -> {
                val today = calendar.today(slot.gameId) ?: return Origin.Unavailable(UNREGISTERED)
                if (slot.day == today) fetch(slot) else build(slot)
            }
        }
    }

    /** A friend's puzzle as it was verified when it opened here; it has no edition number. */
    private suspend fun friend(slot: PlaySlot.Custom): Origin {
        val row = ShareCode.parse(slot.puzzleId)?.let { received.get(it) }
            ?: return Origin.Unavailable(FRIEND_MISSING, retryable = false)
        val puzzle = Puzzle(row.gameId, row.variant, row.sharedFormatVersion, row.puzzleBytes)
        return Origin.Fetched(puzzle, banner = null, puzzleNumber = null)
    }

    /** The slot's saved row as stored, read through the [lane] after every save queued before it; never deletes. */
    suspend fun savedRow(slot: PlaySlot): ByteArray? = lane.run { saved.load(slot) }

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

    private suspend fun fetch(slot: PlaySlot.Daily): Origin = when (val fetched = daily.today(slot.gameId)) {
        is DailyToday.Fresh -> Origin.Fetched(fetched.puzzle, banner = null, puzzleNumberOf(slot))
        is DailyToday.Cached -> Origin.Fetched(fetched.puzzle, degradeBannerMessage(fetched.error), puzzleNumberOf(slot))
        is DailyToday.Unavailable -> Origin.Unavailable(unavailableMessage(fetched.error))
    }

    private suspend fun build(slot: PlaySlot.Daily): Origin =
        pastDay(slot.day, pastDays(slot.gameId, slot.day, puzzleNumberOf(slot)))

    private fun pastDay(day: LocalDate, opened: PastDayOutcome): Origin = when (opened) {
        is PastDayOutcome.Ready -> Origin.Restored(opened.session)
        PastDayOutcome.Missing -> Origin.Unavailable(DAY_MISSING)
        PastDayOutcome.OutOfRange -> Origin.Unavailable(PAST_DAY_OUT_OF_RANGE, retryable = false)
        is PastDayOutcome.Failed -> Origin.Unavailable(
            pastDayUnavailableMessage(day, opened.error),
            retryable = opened.error is FetchError.Unavailable || opened.error is FetchError.Throttled,
        )
        is PastDayOutcome.Unreadable -> Origin.Unavailable(DAY_UNREADABLE)
    }

    /** The leg's puzzle, read from its match: a leg always opens as a replay, never a fresh board. */
    private suspend fun leg(slot: PlaySlot.Leg, plugin: GamePlugin<*, *, *>): Origin {
        val code = ShareCode.parse(slot.matchCode) ?: return Origin.Unavailable(DUEL_PUZZLE_MISSING, retryable = false)
        val match = when (val fetched = matches.observe(code).first()) {
            is MatchFetch.Found -> fetched.match
            MatchFetch.Offline -> return Origin.Unavailable(DUEL_OFFLINE)
            MatchFetch.NotFound -> return Origin.Unavailable(DUEL_PUZZLE_MISSING, retryable = false)
            is MatchFetch.UnsupportedVersion -> return Origin.Unavailable(DUEL_NEEDS_UPDATE, retryable = false)
        }
        val ref = match.legs.getOrNull(slot.legIndex)?.ref?.takeIf { it.gameId == slot.gameId }
            ?: return Origin.Unavailable(DUEL_PUZZLE_MISSING, retryable = false)
        return when (ref) {
            is LegRef.Daily -> dailyLeg(ref, plugin)
            is LegRef.Custom -> friendLeg(ref, plugin)
        }
    }

    /** A day leg; today's is fetched when the day isn't captured yet, as the daily slot would. */
    private suspend fun dailyLeg(ref: LegRef.Daily, plugin: GamePlugin<*, *, *>): Origin {
        val opened = pastDays(ref.gameId, ref.day, ref.number)
        if (opened != PastDayOutcome.Missing || ref.day != calendar.today(ref.gameId)) return pastDay(ref.day, opened)
        val key = PuzzleKey.Daily(ref.day, ref.number)
        return when (val fetched = daily.today(ref.gameId)) {
            is DailyToday.Fresh -> playAgainBuild(plugin, fetched.puzzle, key)
            is DailyToday.Cached -> playAgainBuild(plugin, fetched.puzzle, key)
            is DailyToday.Unavailable -> Origin.Unavailable(unavailableMessage(fetched.error))
        }
    }

    /** A friend's leg, verified and stored on its first open; later opens read the stored row. */
    private suspend fun friendLeg(ref: LegRef.Custom, plugin: GamePlugin<*, *, *>): Origin =
        when (val opened = withContext(dispatchers.default) { loadShared(ref.code) }) {
            is OpenOutcome.Opened -> opened.puzzle.takeIf { it.gameId == ref.gameId }?.let { row ->
                val puzzle = Puzzle(row.gameId, row.variant, row.sharedFormatVersion, row.puzzleBytes)
                playAgainBuild(plugin, puzzle, PuzzleKey.Custom(ref.code.value))
            } ?: Origin.Unavailable(DUEL_PUZZLE_MISSING, retryable = false)
            OpenOutcome.Offline -> Origin.Unavailable(DUEL_OFFLINE)
            is OpenOutcome.NeedsUpdate -> Origin.Unavailable(DUEL_NEEDS_UPDATE, retryable = false)
            OpenOutcome.NotFound, OpenOutcome.NoLongerAvailable, OpenOutcome.FailedCheck, is OpenOutcome.UnknownGame ->
                Origin.Unavailable(DUEL_PUZZLE_MISSING, retryable = false)
        }

    private fun playAgainBuild(plugin: GamePlugin<*, *, *>, puzzle: Puzzle, key: PuzzleKey): Origin =
        when (val session = plugin.playAgainSession(puzzle, key)) {
            is DecodeResult.Ok -> Origin.Restored(session.value)
            is DecodeResult.Err -> Origin.Unavailable(PUZZLE_UNREADABLE, retryable = false)
        }

    /** The day's puzzleNumber from the cache's history rows (null when the day has none). */
    private suspend fun puzzleNumberOf(slot: PlaySlot.Daily): Int? =
        daily.observeHistory(slot.gameId).first().firstOrNull { it.day == slot.day }?.puzzleNumber

    private companion object {
        const val UNREGISTERED = "This game isn't available in this build."
        const val DAY_MISSING = "That day is no longer saved on this device."
        const val DAY_UNREADABLE = "This day didn't load right, so it can't be played again."
        const val FRIEND_MISSING = "This puzzle is no longer saved on this device."
        const val DUEL_OFFLINE = "You're offline. A duel's puzzle needs a connection the first time it opens."
        const val DUEL_NEEDS_UPDATE = "Update PuzzleBox to play this puzzle."
        const val DUEL_PUZZLE_MISSING = "This puzzle isn't available."
        const val PUZZLE_UNREADABLE = "This puzzle didn't load right, so it can't be played."
    }
}

/** Copy for a day outside the game's archive, or one its source says doesn't exist. */
internal const val PAST_DAY_OUT_OF_RANGE = "This puzzle isn't available."

/** Why an archive day that isn't on the device could not be fetched. */
internal fun pastDayUnavailableMessage(day: LocalDate, error: FetchError): String = when (error) {
    is FetchError.NotFound -> PAST_DAY_OUT_OF_RANGE
    // Unavailable covers 5xx and unreadable bodies too, so offline is named as likely, not certain.
    is FetchError.Unavailable ->
        "Couldn't load ${day.format(PAST_DAY)}. You may be offline — it needs a connection the first time it opens."
    is FetchError.Untrusted -> "This puzzle couldn't be read. Try again later."
    is FetchError.Throttled -> "Can't load ${day.format(PAST_DAY)} right now. Wait a minute and try again."
}

// The copy is English-only, so the date follows it rather than the device locale.
private val PAST_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)
