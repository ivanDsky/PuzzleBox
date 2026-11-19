package com.ivandsky.puzzlebox.domain

class GetViaB(private val leaf: LeafThing) {
    operator fun invoke(): Int = leaf.value
}
