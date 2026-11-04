package com.ivandsky.puzzlebox.domain

sealed class Outcome {
    data object Won : Outcome()
    data object Lost : Outcome()
}
