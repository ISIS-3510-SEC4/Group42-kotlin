package com.example.espoti

import android.app.Application
import com.example.espoti.di.AppContainer

/**
 * Process entry point (registered in AndroidManifest.xml). Prepares the
 * offline layers before any screen or background worker uses them.
 */
class EspotiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        // Sends anything saved offline in a previous session (it waits for a
        // network and does nothing if there is nothing pending).
        AppContainer.syncScheduler.scheduleSync()
    }
}
