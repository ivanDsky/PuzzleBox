package com.ivandsky.puzzlebox.app.device

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Swaps the merged manifest's PuzzleBoxApplication for a plain [Application], so the
 * production Koin graph (and its LinkedIn-backed daily) never starts; each test starts its
 * own fakes graph through [DeviceShell].
 */
class PuzzleBoxTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
