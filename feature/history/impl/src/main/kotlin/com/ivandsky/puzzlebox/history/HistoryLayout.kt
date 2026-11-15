package com.ivandsky.puzzlebox.history

/** History's pane arrangement, mapped from the shared `PanePolicy` decision. */
enum class HistoryLayout {
    /** One pane: phones, portrait, and landscape windows too short for two panes. */
    SinglePane,

    /** Tablet landscape: list pane beside a detail pane. */
    TwoPane,
}
