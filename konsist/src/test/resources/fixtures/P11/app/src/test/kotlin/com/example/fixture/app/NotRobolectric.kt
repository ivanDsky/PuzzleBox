package com.example.fixture.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(org.junit.runners.JUnit4::class)
class NotRobolectric {
    @Test
    fun runs() = Unit
}

class NoRunWith {
    @Test
    fun runs() = Unit
}
