package com.ivandsky.puzzlebox.app.nav

/**
 * The MainTabsLargeBar flag as the app's DI read it once per graph (backlog #1597), so the shell's chrome needs no flag
 * read of its own: [largeBar] puts the phone's avatar in the top bar of every main tab, not Today's alone.
 */
internal class MainTabBars(val largeBar: Boolean)
