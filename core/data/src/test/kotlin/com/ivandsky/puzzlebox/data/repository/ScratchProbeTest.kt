package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.network.TangoGameExtractor
import com.ivandsky.puzzlebox.data.network.TangoWireParser
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import java.time.LocalDate
import org.junit.Test

class ScratchProbeTest {
    @Test
    fun probe() {
        val body = requireNotNull(javaClass.classLoader).getResource("fixtures/linkedin-tango-rsc-301.txt")!!.readText()
        val match = TangoGameExtractor.extract(body)!!
        println("PROBE raw=" + match.tangoGamePuzzleJson.take(220))
        val daily = TangoWireParser.parse(match.tangoGamePuzzleJson, LocalDate.of(2026, 11, 2), match.editionText) as DecodeResult.Ok
        println("PROBE givens=" + daily.value.puzzle.givens.entries.sortedBy { it.key })
        println("PROBE count=" + TangoUniqueness.countSolutions(daily.value.puzzle, limit = 5))
        println("PROBE parserCls=" + TangoWireParser::class.java.protectionDomain.codeSource.location)
    }
}
