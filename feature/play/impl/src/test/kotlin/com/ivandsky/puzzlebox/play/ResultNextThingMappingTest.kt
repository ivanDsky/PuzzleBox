package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.nextthing.NextThing
import com.ivandsky.puzzlebox.domain.nextthing.ResultNextThing
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.resultui.ResultPrimaryAction
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** L3, L4: each next thing the chain can offer becomes the primary that names and opens the same thing. */
class ResultNextThingMappingTest {

    private val queens = GameId("queens")
    private val code = checkNotNull(ShareCode.parse("AAAAA2"))
    private val names: (GameId) -> String = { "The ${it.value}" }

    @Test
    fun `the next daily becomes Next with the game's display name`() {
        assertEquals(ResultPrimaryAction.Next(queens, "The queens"), NextThing.NextDaily(queens).toPrimaryAction(names))
    }

    @Test
    fun `a friend to challenge becomes Challenge with the same friend`() {
        assertEquals(ResultPrimaryAction.ChallengeFriend("ann", "Ann"), NextThing.ChallengeFriend("ann", "Ann").toPrimaryAction(names))
    }

    @Test
    fun `a missed day becomes that game's day, named`() {
        val day = LocalDate.parse("2026-11-12")
        assertEquals(ResultPrimaryAction.MissedDay(queens, day, "The queens"), NextThing.MissedDay(queens, day).toPrimaryAction(names))
    }

    @Test
    fun `a friend's puzzle keeps its code, game and author`() {
        assertEquals(
            ResultPrimaryAction.FriendPuzzle(code, queens, "Ann"),
            NextThing.FriendPuzzle(code, queens, "Ann").toPrimaryAction(names),
        )
    }

    @Test
    fun `a community puzzle keeps its code, game and author`() {
        assertEquals(
            ResultPrimaryAction.CommunityPuzzle(code, queens, "Zed"),
            NextThing.CommunityPuzzle(code, queens, "Zed").toPrimaryAction(names),
        )
    }

    @Test
    fun `done for today becomes all done`() {
        assertEquals(ResultPrimaryAction.AllDone, NextThing.DoneForToday(Instant.EPOCH).toPrimaryAction(names))
    }

    @Test
    fun `the Challenge row's friend is carried beside a primary that is something else`() {
        val facts = ResultNextThing(listOf(Moment.PersonalBest), NextThing.NextDaily(queens), NextThing.ChallengeFriend("ann", "Ann"))
            .toResultFacts(names)

        assertEquals(
            ResultNextFacts(
                moments = listOf(Moment.PersonalBest),
                primary = ResultPrimaryAction.Next(queens, "The queens"),
                challenge = ResultPrimaryAction.ChallengeFriend("ann", "Ann"),
            ),
            facts,
        )
    }

    @Test
    fun `a next thing not yet known leaves the primary pending`() {
        assertEquals(ResultNextFacts(moments = listOf(Moment.FullSetDone())), ResultNextThing(listOf(Moment.FullSetDone())).toResultFacts(names))
    }
}
