package com.ivandsky.puzzlebox.insights.di

import com.ivandsky.puzzlebox.insights.share.RecapShareCleaner
import com.ivandsky.puzzlebox.insights.share.RecapShareRenderer
import com.ivandsky.puzzlebox.insights.share.RecapShareUris
import com.ivandsky.puzzlebox.insights.share.RecapShareViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The recap share card's wiring, included by [insightsModule]: the share files (renderer, Uris, cleaner) are singletons
 * over the app's cache directory; [RecapShareCleaner] is the one :app starts. The card's view model takes the story's
 * period as a Koin parameter.
 */
val recapShareModule = module {
    single { RecapShareRenderer(cacheRoot = androidContext().cacheDir, dispatchers = get()) }
    single { RecapShareUris(androidContext()) }
    single { RecapShareCleaner(renderer = get(), scope = get()) }

    viewModel { params ->
        val uris = get<RecapShareUris>()
        RecapShareViewModel(
            period = params.get(),
            identity = get(),
            cloudSync = get(),
            puzzleDay = get(),
            renderer = get(),
            uriOf = uris::of,
        )
    }
}
