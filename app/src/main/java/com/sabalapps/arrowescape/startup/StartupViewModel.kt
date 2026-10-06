package com.sabalapps.arrowescape.startup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Starts the startup work the moment the activity is created and keeps it — and its progress —
 * across anything that recreates the activity, so the loading screen can come and go without
 * the work being run twice.
 */
class StartupViewModel(application: Application) : AndroidViewModel(application) {

    private val coordinator = StartupCoordinator(AppStartup.steps(application))

    /** How far the real work has got. */
    val progress: StateFlow<StartupProgress> = coordinator.progress

    init {
        viewModelScope.launch { coordinator.run() }
    }
}
