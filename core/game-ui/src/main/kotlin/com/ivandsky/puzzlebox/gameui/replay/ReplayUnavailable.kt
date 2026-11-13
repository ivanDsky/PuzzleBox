package com.ivandsky.puzzlebox.gameui.replay

import com.ivandsky.puzzlebox.game.CompletionSource

/** Why a stored solve has no replay. */
enum class ReplayUnavailable {
    ImportedFromLinkedIn,
    NotRecorded,
    ;

    companion object {
        /** The reason for a stored solve that came from [source]. */
        fun of(source: CompletionSource): ReplayUnavailable =
            if (source == CompletionSource.LinkedIn) ImportedFromLinkedIn else NotRecorded
    }
}
