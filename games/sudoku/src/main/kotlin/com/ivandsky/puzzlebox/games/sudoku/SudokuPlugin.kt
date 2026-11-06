package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuSessionCodec
import com.ivandsky.puzzlebox.games.sudoku.render.SudokuRendererModel

/** The plugin for one Sudoku game; both games share the engine and differ only in [definition]. */
fun sudokuPlugin(definition: SudokuDefinition): GamePlugin<SudokuState, SudokuSession, SudokuRendererModel> =
    GamePlugin(
        definition = definition,
        rules = SudokuGameRules,
        session = SudokuSessionCodec,
        score = SudokuScoreModel(),
        stateOf = { session -> SudokuState(session.puzzle, session.board) },
        initialSession = { puzzle ->
            if (puzzle.gameId != definition.id) {
                DecodeResult.Err(DecodeError.Malformed("foreign envelope: ${puzzle.gameId}"))
            } else {
                when (val decoded = definition.decodePuzzle(puzzle.payload)) {
                    is DecodeResult.Ok -> DecodeResult.Ok(SudokuSession(ref = null, puzzle = decoded.value))
                    is DecodeResult.Err -> decoded
                }
            }
        },
        refOf = { date, puzzleNumber -> SudokuPuzzleRef(definition.id, date, puzzleNumber) },
        replaySession = { cached, day, puzzleNumber ->
            when (val decoded = definition.decodePuzzle(cached.payload)) {
                is DecodeResult.Ok -> {
                    val session = SudokuSession(ref = SudokuPuzzleRef(definition.id, day, puzzleNumber), puzzle = decoded.value)
                    DecodeResult.Ok(SessionBlob.encode(definition.id, SudokuSessionCodec.serialize(session)))
                }
                is DecodeResult.Err -> decoded
            }
        },
        rendererModel = { state, hint, _ -> SudokuRendererModel.of(state, hint) },
    )
