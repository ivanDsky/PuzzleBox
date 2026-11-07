package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.Command

/**
 * Strands draft edits as undoable commands, one step each. Board-wide replacement (clear,
 * import) uses [com.ivandsky.puzzlebox.game.editor.ReplaceDraft], not a Strands command.
 */
sealed interface StrandsDraftCommand : Command<StrandsDraft> {
    /** Appends [path]; revert drops the last path. */
    data class AddPath(val path: StrandsDraftPath) : StrandsDraftCommand {
        override fun apply(state: StrandsDraft): StrandsDraft = state.copy(paths = state.paths + path)
        override fun revert(state: StrandsDraft): StrandsDraft = state.copy(paths = state.paths.dropLast(1))
    }

    /** Replaces the path at [index]; the board letters go from [lettersBefore] to [lettersAfter] so undo restores the blanks. */
    data class EditPath(
        val index: Int,
        val before: StrandsDraftPath,
        val after: StrandsDraftPath,
        val lettersBefore: String,
        val lettersAfter: String,
    ) : StrandsDraftCommand {
        override fun apply(state: StrandsDraft): StrandsDraft = state.replacePath(index, after).copy(letters = lettersAfter)
        override fun revert(state: StrandsDraft): StrandsDraft = state.replacePath(index, before).copy(letters = lettersBefore)
    }

    /**
     * Removes the path at [index]. The spangram follows its path down the list, and clears when
     * that path is the one removed; [spangramBefore] restores it on revert.
     */
    data class RemovePath(val index: Int, val path: StrandsDraftPath, val spangramBefore: Int) : StrandsDraftCommand {
        override fun apply(state: StrandsDraft): StrandsDraft {
            val spangram = when {
                state.spangram == index -> StrandsDraft.NO_SPANGRAM
                state.spangram > index -> state.spangram - 1
                else -> state.spangram
            }
            return state.copy(paths = state.paths.filterIndexed { i, _ -> i != index }, spangram = spangram)
        }

        override fun revert(state: StrandsDraft): StrandsDraft {
            val restored = state.paths.toMutableList().apply { add(index.coerceIn(0, size), path) }
            return state.copy(paths = restored, spangram = spangramBefore)
        }
    }

    /** Picks the path at [index] as the spangram; [before] is the previous pick, or NO_SPANGRAM. */
    data class SetSpangram(val index: Int, val before: Int) : StrandsDraftCommand {
        override fun apply(state: StrandsDraft): StrandsDraft = state.copy(spangram = index)
        override fun revert(state: StrandsDraft): StrandsDraft = state.copy(spangram = before)
    }

    data class SetClue(val clue: String, val before: String) : StrandsDraftCommand {
        override fun apply(state: StrandsDraft): StrandsDraft = state.copy(clue = clue)
        override fun revert(state: StrandsDraft): StrandsDraft = state.copy(clue = before)
    }
}

private fun StrandsDraft.replacePath(index: Int, path: StrandsDraftPath): StrandsDraft =
    copy(paths = paths.mapIndexed { i, existing -> if (i == index) path else existing })
