package com.ivandsky.puzzlebox.designsystem

/**
 * Prototype 141 (throwaway): how the tab pager ([UnderlineTabPager]) changes page. [Today] is the shipped behaviour,
 * untouched; the others are the options in `.scratch/nav-rethink/prototypes/141-tab-swipe/FINDINGS.md`. [label] is the
 * switcher's chip and [caption] the line over the chips.
 */
enum class TabSwipeOption(val label: String, val caption: String) {
    Today("Today", "Today: slide, neighbour built on swipe, pane swaps after"),
    A("A", "A: slide, pages kept, pane crossfades with the swipe"),
    B("B", "B: swipe slides; a tap fades through with a short slide"),
    C("C", "C: next page covers, old one drifts under a scrim"),
    D("D", "D: slide; a tap two tabs away fades through in place"),
}
