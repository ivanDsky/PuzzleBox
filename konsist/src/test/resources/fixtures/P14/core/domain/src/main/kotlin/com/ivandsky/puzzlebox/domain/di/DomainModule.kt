package com.ivandsky.puzzlebox.domain.di

import com.ivandsky.puzzlebox.domain.GetDomainTested
import com.ivandsky.puzzlebox.domain.GetShared
import com.ivandsky.puzzlebox.domain.GetSingle

/** A `di` file is neither a subject nor a consumer: it registers every use case. */
val domainModule = listOf(GetDomainTested::class, GetShared::class, GetSingle::class)
