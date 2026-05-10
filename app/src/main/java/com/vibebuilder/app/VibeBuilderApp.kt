package com.vibebuilder.app

import android.app.Application
import com.vibebuilder.app.di.ServiceLocator

/**
 * Application class responsible for bootstrapping lightweight app dependencies.
 */
class VibeBuilderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }
}
