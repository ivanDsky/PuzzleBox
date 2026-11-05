package com.ivandsky.puzzlebox.x.api

import androidx.navigation3.runtime.NavKey
import java.time.LocalDate

data class BadModelKey(val day: LocalDate) : NavKey
