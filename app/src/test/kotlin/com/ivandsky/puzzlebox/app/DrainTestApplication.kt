package com.ivandsky.puzzlebox.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Method
import org.robolectric.Shadows.shadowOf
import org.robolectric.TestLifecycleApplication

/**
 * The plain `Application` every app Robolectric test boots with (`@Config(application = …)`), plus
 * two jobs: decode one image on the SDK main thread before the first test, and run the main looper
 * dry after every test.
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
 *
 * [onCreate] decodes one real image on the SDK main thread because the first `ImageDecoder` call
 * in a JVM runs a one-time native init (`JniConstants` class lookups) that fails on a Coil
 * `Dispatchers` worker: under `-Xcheck:jni` it is "Using JNIEnv in the wrong thread"; unchecked,
 * HotSpot's `FindClass` throws a `StackOverflowError`, `JniConstants` logs `Class not found:
 * java/nio/IntBuffer` and the native library `abort()`s the whole test JVM (#1325). A ghost
 * avatar on the Ranks hub was the first decode in `LinkedInGhostIsolationE2ETest`. Doing it up
 * front here does not depend on test order or on a warm-up class having run first.
 */
class DrainTestApplication : Application(), TestLifecycleApplication {
    override fun onCreate() {
        super.onCreate()
        nativeImageDecoderWarm
    }

    override fun beforeTest(method: Method) = Unit

    override fun prepareTest(test: Any) = Unit

    override fun afterTest(method: Method) {
        shadowOf(Looper.getMainLooper()).idle()
    }
}

// A real PNG through the same `ImageDecoder.createSource(File)` path Coil takes. Lazy: once per
// sandbox classloader, which is once per JVM here, so only the first test pays for it.
private val nativeImageDecoderWarm by lazy {
    val png = ByteArrayOutputStream().also {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()
    val file = File.createTempFile("native-warm-up", ".png").apply {
        deleteOnExit()
        writeBytes(png)
    }
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)).recycle()
}
