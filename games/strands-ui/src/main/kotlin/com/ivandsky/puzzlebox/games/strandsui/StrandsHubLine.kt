package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strands.codec.StrandsSessionCodec

/** Strands' Up-next line: today's clue before play, the word count during it, nothing once solved. */
internal object StrandsHubLine {
    fun of(today: BoardPreview): String? = when (today) {
        is BoardPreview.Fresh -> today.puzzle.takeIf { it.gameId == StrandsDefinition.id }
            ?.let { (StrandsCodec.decode(it.payload) as? DecodeResult.Ok)?.value?.clue }
        is BoardPreview.InProgress -> (StrandsSessionCodec.deserialize(today.bytes) as? DecodeResult.Ok)?.value
            ?.state?.let { "${it.found.size} of ${it.puzzle.totalWords} words" }
        is BoardPreview.Solved -> null
    }
}
