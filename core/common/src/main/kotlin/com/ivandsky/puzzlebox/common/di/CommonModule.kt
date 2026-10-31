package com.ivandsky.puzzlebox.common.di

import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import org.koin.dsl.module

val commonModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
}
