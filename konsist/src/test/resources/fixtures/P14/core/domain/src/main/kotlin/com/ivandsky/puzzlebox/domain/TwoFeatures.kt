package com.ivandsky.puzzlebox.domain

class GetShared(private val helper: SharedHelper) {
    operator fun invoke(): Int = helper.value
}
