package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flagsets.FlagSetLike
import org.example.com.ivandsky.puzzlebox.common.flags.Foreign

class NearMiss(private val dispatchers: DispatcherProvider, private val set: FlagSetLike, private val foreign: Foreign)
