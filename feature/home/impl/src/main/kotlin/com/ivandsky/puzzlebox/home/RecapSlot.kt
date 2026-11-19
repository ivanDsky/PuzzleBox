package com.ivandsky.puzzlebox.home

/**
 * The recap Today offers, built where recaps are known (the app) and hosted here as plain values: Home never names
 * an insights type. [label] is the period as the player reads it ("October 2026"); [onOpen] opens that recap and
 * [onDismiss] hides its card for that period only. A null slot is no card.
 */
class RecapSlot(val label: String, val onOpen: () -> Unit, val onDismiss: () -> Unit)
