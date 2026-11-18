package com.ivandsky.puzzlebox.domain

class GetSingle(private val helper: SingleHelper) {
    operator fun invoke(): Int = helper.value
}
