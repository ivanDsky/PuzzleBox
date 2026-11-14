package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import org.koin.dsl.module

/**
 * The owner scope for a nested `dataModule` graph that has no account behind it: no filter and no stamp, so its
 * repositories read and write exactly as they did before accounts.
 */
internal val unfilteredOwnerModule = module { single<OwnerScope> { FakeOwnerScope() } }
