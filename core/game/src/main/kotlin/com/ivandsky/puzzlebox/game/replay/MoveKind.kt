package com.ivandsky.puzzlebox.game.replay

/** What a board-changing move was, for the timeline colours. Declared lowest priority first: Mistake > Hint > Undo > Normal. */
enum class MoveKind { Normal, Undo, Hint, Mistake }
