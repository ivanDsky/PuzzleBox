package com.ivandsky.puzzlebox.x.api

import androidx.navigation3.runtime.NavKey

data class BadByteArrayKey(val gameId: String, val blob: ByteArray) : NavKey
