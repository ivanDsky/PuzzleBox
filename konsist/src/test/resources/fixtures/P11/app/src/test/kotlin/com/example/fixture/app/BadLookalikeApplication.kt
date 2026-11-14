package com.example.fixture.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = NotDrainTestApplication::class)
class BadLookalikeApplication {
    @Test
    fun runs() = Unit
}
