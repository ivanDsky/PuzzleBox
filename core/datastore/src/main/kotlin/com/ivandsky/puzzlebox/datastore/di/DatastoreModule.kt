package com.ivandsky.puzzlebox.datastore.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val datastoreModule = module {
    // The DataStore owns its file for the whole process, so its scope lives as long
    // as the singleton: I/O dispatcher from the provider, SupervisorJob so one failed
    // write never cancels the scope.
    single {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(get<DispatcherProvider>().io + SupervisorJob()),
        ) { get<Context>().filesDir.resolve("puzzlebox.preferences_pb") }
    }
    single { PuzzleBoxPreferencesDataSource(get()) }
}
