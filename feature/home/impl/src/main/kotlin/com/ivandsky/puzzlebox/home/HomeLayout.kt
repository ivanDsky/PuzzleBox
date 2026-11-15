package com.ivandsky.puzzlebox.home

/** Home's pane arrangement, mapped from the shared `PanePolicy` decision. */
enum class HomeLayout {
    /** One pane: phones, portrait, and landscape windows too short for two panes. */
    SinglePane,

    /** Tablet landscape: list pane beside a detail pane. */
    TwoPane,
}
