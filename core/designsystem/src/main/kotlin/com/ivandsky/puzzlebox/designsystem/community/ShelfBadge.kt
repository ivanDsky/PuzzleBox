package com.ivandsky.puzzlebox.designsystem.community

/** The status a shelf card carries in its top-right corner: new this week, from a friend, or yours. */
enum class ShelfBadge(val label: String) {
    New("NEW"),
    Friend("Friend"),
    You("You"),
}
