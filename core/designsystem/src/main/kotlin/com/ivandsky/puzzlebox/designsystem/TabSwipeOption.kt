package com.ivandsky.puzzlebox.designsystem

/**
 * Prototype 141 (throwaway): how the tab pager ([UnderlineTabPager]) changes page. [Today] is the shipped behaviour,
 * untouched; the others are the options in `.scratch/nav-rethink/prototypes/141-tab-swipe/FINDINGS.md`. [label] is the
 * switcher's chip and [caption] the line over the chips: what the option does and the gesture that shows it.
 */
enum class TabSwipeOption(val label: String, val caption: String) {
    Today("Today", "Today: slide; the pane swaps after the swipe"),
    A("A", "A: slide; the pane crossfades with the swipe. Try a swipe"),
    B("B", "B: as A, but a tab tap fades through. Try tapping a tab"),
    C("C", "C: the next page covers the old one. Try a slow swipe"),
    D("D", "D: as A, but a far tap fades through. Try Community tabs"),
    E("E", "E: fade through, list and pane together. Swipe or tap"),
}
