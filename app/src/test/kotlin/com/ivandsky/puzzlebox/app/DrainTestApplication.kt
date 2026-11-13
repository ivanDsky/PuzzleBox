package com.ivandsky.puzzlebox.app

import android.app.Application
import android.os.Looper
import java.lang.reflect.Method
import org.robolectric.Shadows.shadowOf
import org.robolectric.TestLifecycleApplication

/**
 * The plain `Application` every app Robolectric test boots with (`@Config(application = …)`), plus
 * one job: run the main looper dry after every test.
 *
 * Compose's process-wide `AndroidUiDispatcher.Main` schedules its next run with two flags
 * (`scheduledTrampolineDispatch`, `scheduledFrameDispatch`) that only the posted task clears.
 * A Compose v2 rule's teardown can write global snapshot state, which makes
 * `GlobalSnapshotManager`'s consumer dispatch onto it; Robolectric then resets the looper right
 * after the test and drops that task. All app test classes share one sandbox, so the flags stay
 * set for the rest of the JVM: `AndroidUiDispatcher.Main` never runs again,
 * `GlobalSnapshotManager` never applies another write, and a later test's Paging
 * `LazyPagingItems` state never reaches composition (History's rows never appear).
 *
 * [afterTest] runs after the test's rules (the Compose rule's teardown) and before Robolectric's
 * looper reset, which is the only window where idling clears the flags.
 */
class DrainTestApplication : Application(), TestLifecycleApplication {
    override fun beforeTest(method: Method) = Unit

    override fun prepareTest(test: Any) = Unit

    override fun afterTest(method: Method) {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
