package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.play.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.play.domain.linkedin.SubmitLinkedInResult
import org.koin.dsl.module

/** The result's LinkedIn post use cases, included by [playModule]: [ObserveLinkedInPost] and [SubmitLinkedInResult]. */
val linkedInModule = module {
    factory { ObserveLinkedInPost(posts = get()) }
    factory { SubmitLinkedInResult(posts = get()) }
}
