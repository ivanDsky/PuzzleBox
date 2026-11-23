package com.ivandsky.puzzlebox.insights.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Insights feature wiring: the per-feature modules it includes, each in
 * its own file so a new card or use case adds a file here, not an edit to this one. The recap's use cases and its story
 * view model are [recapModule]; its share files and card view model are [recapShareModule]; the stats page's use cases
 * and view model are [statsModule]; the hub's view model is [insightsHubModule]; the kept weeks' one reader is
 * [rankHistoryModule].
 *
 * The stats page's vs friends section reads Friends Overall like the recap's friends card; its rank history section reads
 * the kept weeks through the one reader. The friends standing is the one recap read that goes to the network for a month,
 * kept apart from the local providers; a year's comes from the kept weekly history. A story's period and a stats page's
 * game arrive as Koin parameters, so each opened month, year or game has its own.
 * The share files (renderer, Uris, cleaner) are singletons over the app's cache directory; the cleaner is the one :app
 * starts. The due-recap use case is the one :app reads, for Home's recap card. Use cases that several features share come
 * from :core:domain's `insightsDomainModule`.
 */
val insightsModule: Module = module {
    includes(rankHistoryModule, recapModule, recapShareModule, statsModule, insightsHubModule)
}
