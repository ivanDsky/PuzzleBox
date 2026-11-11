package com.ivandsky.puzzlebox.compete.tab

import com.ivandsky.puzzlebox.domain.DuelRowKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** A finished row's title is worded from my side, and says only "Finished" when the scores were not kept. */
class FinishedTitleTest {

    @Test
    fun aWinNamesMeFirst() = assertEquals("You won 2–1 vs Sam", finishedTitle(DuelRowKind.Finished(2, 1), "Sam"))

    @Test
    fun aLossNamesTheOpponent() = assertEquals("Sam won 2–0", finishedTitle(DuelRowKind.Finished(0, 2), "Sam"))

    @Test
    fun aDrawSaysDraw() = assertEquals("Draw 1–1 vs Sam", finishedTitle(DuelRowKind.Finished(1, 1), "Sam"))

    @Test
    fun noScoresSaysFinished() {
        assertEquals("Finished vs Sam", finishedTitle(DuelRowKind.Finished(null, null), "Sam"))
        assertEquals("Finished vs Sam", finishedTitle(DuelRowKind.Finished(2, null), "Sam"))
    }
}
