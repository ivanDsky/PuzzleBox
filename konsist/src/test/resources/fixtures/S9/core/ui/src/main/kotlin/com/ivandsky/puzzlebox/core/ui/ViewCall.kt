package com.ivandsky.puzzlebox.core.ui

fun a(view: View) = view.performHapticFeedback(buzz)

fun b() = LocalView.current.performHapticFeedback(buzz)
