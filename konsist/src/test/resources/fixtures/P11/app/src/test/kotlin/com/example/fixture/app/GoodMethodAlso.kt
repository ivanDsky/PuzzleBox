package com.example.fixture.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = DrainTestApplication::class)
class GoodMethodAlso {
    @Config(qualifiers = "w1280dp-h800dp-land")
    @Test
    fun runs() = Unit
}
