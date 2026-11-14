package com.example.fixture.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [34],
    application = DrainTestApplication::class,
)
class GoodAndroidJUnit4 {
    @Test
    fun runs() = Unit
}
