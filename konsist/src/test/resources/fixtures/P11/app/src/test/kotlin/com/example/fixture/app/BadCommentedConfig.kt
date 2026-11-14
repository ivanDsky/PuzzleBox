package com.example.fixture.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// @Config(application = DrainTestApplication::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // application = DrainTestApplication::class
class BadCommentedConfig {
    private val note = "@Config(application = DrainTestApplication::class)"

    @Test
    fun runs() = Unit
}
