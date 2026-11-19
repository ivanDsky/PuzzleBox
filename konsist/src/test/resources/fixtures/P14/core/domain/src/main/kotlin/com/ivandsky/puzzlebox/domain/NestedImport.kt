package com.ivandsky.puzzlebox.domain

class ObserveNested {
    operator fun invoke(): Binding = Binding()

    class Binding
}
