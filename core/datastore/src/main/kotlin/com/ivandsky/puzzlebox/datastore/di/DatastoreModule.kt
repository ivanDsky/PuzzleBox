package com.ivandsky.puzzlebox.datastore.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.datastore.KeptSessionsDataSource
import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Kept sessions get their own file (spec A3): the `puzzlebox` preferences gate the first frame
 * (theme), so they stay single-owner and low-churn. The qualifier keeps the two DataStores apart.
 */
private val KEPT_SESSIONS = named("kept_sessions")

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
    single(KEPT_SESSIONS) {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(get<DispatcherProvider>().io + SupervisorJob()),
        ) { get<Context>().filesDir.resolve("kept_sessions.preferences_pb") }
    }
    single { KeptSessionsDataSource(get(KEPT_SESSIONS)) }
}
