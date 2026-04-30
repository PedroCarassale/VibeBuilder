package com.vibebuilder.app

import android.app.Application

/**
 * Application class. Currently a thin wrapper; the in-memory repository lives inside
 * [com.vibebuilder.app.di.ServiceLocator] and is initialized lazily on first access.
 */
class VibeBuilderApp : Application()
