package com.ivandsky.puzzlebox.compete.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

internal const val ARCHIVE_PAGE_SIZE = 30

internal val DAY_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * The daily archive of every game that has one, newest day first, read [ARCHIVE_PAGE_SIZE] rows at a time so a
 * years-long archive is never listed whole. A day with no puzzle number has no row. Each row is the day's puzzle as a
 * [PuzzleSource.PastDay] pick; whether the player solved it comes from the completion keys they hold.
 */
internal class DailyArchiveSource(
    private val daily: DailyRepository,
    private val registry: GameRegistry,
) {
    /** Where the next page starts: for each game with days left, the newest day not read yet. */
    class Cursor internal constructor(internal val next: Map<GameId, LocalDate>)

    class Page internal constructor(internal val rows: List<Day>, val next: Cursor?)

    class Day internal constructor(val ref: LegRef.Daily, val title: String)

    fun start(): Cursor = Cursor(
        registry.all.map { it.definition.id }.mapNotNull { id -> daily.archiveRange(id)?.let { id to it.endInclusive } }.toMap(),
    )

    /** The next [size] numbered days after [from], newest first, the games merged by day; [Page.next] is null once no game has a day left. */
    fun page(from: Cursor, size: Int): Page {
        val next = from.next.toMutableMap()
        val firsts = next.keys.associateWith { daily.archiveRange(it)?.start }
        // A game is done once its next day falls before its first one.
        fun prune() = next.entries.removeAll { (game, day) -> firsts[game]?.let { day < it } != false }
        val rows = ArrayList<Day>(size)
        prune()
        while (rows.size < size && next.isNotEmpty()) {
            val (game, day) = next.entries.maxBy { it.value }.toPair()
            next[game] = day.minusDays(1)
            daily.puzzleNumber(game, day)?.let { number ->
                rows += Day(LegRef.Daily(game, day, number), "${registry.displayName(game)} #$number")
            }
            prune()
        }
        return Page(rows, Cursor(next).takeIf { next.isNotEmpty() })
    }

    /**
     * The rows of the first [pages] pages, read as that count grows. The count is authoritative: when it drops below what
     * was read (the picker opened again) the read starts over from the newest day. [SourceRows.moreDays] says whether a
     * further page exists.
     */
    fun observe(played: Flow<Set<String>>, pages: Flow<Int>): Flow<SourceRows> {
        val read = flow {
            var cursor: Cursor? = start()
            var rows = emptyList<Day>()
            var count = 0
            pages.collect { wanted ->
                if (wanted < count) {
                    cursor = start()
                    rows = emptyList()
                    count = 0
                }
                while (count < wanted) {
                    val from = cursor ?: break
                    val page = page(from, ARCHIVE_PAGE_SIZE)
                    rows = rows + page.rows
                    cursor = page.next
                    count++
                }
                emit(rows to (cursor != null))
            }
        }
        return combine(played, read) { solved, (days, more) -> SourceRows(days.map { it.row(solved) }, moreDays = more) }
    }
    private fun Day.row(solved: Set<String>) = SourceRows.Dated(
        atMs = ref.day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        puzzle = PickablePuzzle(
            entry = ExactPuzzle(ref, authorUid = null, title = title, author = null, source = PuzzleSource.PastDay),
            isNew = false,
            played = ref.wire in solved,
            meta = DAY_FORMAT.format(ref.day),
            code = null,
        ),
    )
}
