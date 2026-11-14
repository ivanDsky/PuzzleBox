package com.example.fixture.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(value = RobolectricTestRunner::class)
// A comment between the annotations is not part of either.
@org.robolectric.annotation.Config(qualifiers = "w411dp-h891dp", application = com.example.fixture.app.DrainTestApplication::class)
class GoodQualified {
    @Test
    fun runs() = Unit
}
