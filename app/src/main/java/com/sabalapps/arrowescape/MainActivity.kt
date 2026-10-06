package com.sabalapps.arrowescape

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.sabalapps.arrowescape.startup.LaunchIntents
import com.sabalapps.arrowescape.startup.LaunchRouter
import com.sabalapps.arrowescape.startup.StartupDestination
import com.sabalapps.arrowescape.startup.StartupViewModel
import com.sabalapps.arrowescape.ui.AppRoot

class MainActivity : ComponentActivity() {

    /** What the launch intent asked for, held until the game is ready to go there — once. */
    private val launchRouter = LaunchRouter()

    /** False until the first Compose frame is up; the system splash stays exactly that long. */
    private var firstFrameReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash: navy, no logo (see Theme.ArrowEscape.Starting). It is held only
        // until the Compose loading screen has its first frame, and then it is removed *at
        // once* — no exit animation — so the one hands straight to the other. A failsafe
        // releases it regardless, so it can never outlive a hung first frame.
        val systemSplash = installSplashScreen()
        super.onCreate(savedInstanceState)
        systemSplash.setKeepOnScreenCondition { !firstFrameReady }
        systemSplash.setOnExitAnimationListener { it.remove() }
        window.decorView.postDelayed({ firstFrameReady = true }, SYSTEM_SPLASH_FAILSAFE_MS)

        // The menus and the game both sit on dark-scrimmed artwork, whichever theme
        // the player picked, so the system bars always carry light icons. The default
        // follows the *system* theme and would put dark icons on dark art.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // A *fresh* launch can carry a destination (the reminder). A recreated activity is
        // handed the same intent again by the system, and replaying it would send the player
        // to the Daily Challenge a second time.
        if (savedInstanceState == null) offerDestinationOf(intent)

        val startup = ViewModelProvider(this)[StartupViewModel::class.java]
        setContent {
            AppRoot(startup = startup, router = launchRouter, debugSink = debugSink())
            // Composed: the first frame is on its way; let the system splash go.
            androidx.compose.runtime.SideEffect { firstFrameReady = true }
        }
    }

    /** The reminder tapped while the game is already open (running, or backgrounded). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerDestinationOf(intent)
    }

    private fun offerDestinationOf(intent: Intent?) {
        launchRouter.offer(
            StartupDestination.fromExtra(intent?.getStringExtra(LaunchIntents.EXTRA_DESTINATION))
        )
        // Spent: a later recreation must not read it again.
        intent?.removeExtra(LaunchIntents.EXTRA_DESTINATION)
    }

    /**
     * Where generator diagnostics go: seed, board size, arrow count, opening
     * free moves and difficulty score, as one logcat line per generated puzzle.
     *
     * Debug builds only. The check is the running package's own
     * `FLAG_DEBUGGABLE` rather than a `BuildConfig` constant, so a release APK
     * gets a no-op sink and the ViewModel has nothing to log — the strings are
     * never even built.
     */
    private fun debugSink(): (String) -> Unit {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return if (debuggable) {
            { line -> Log.d(GENERATOR_LOG_TAG, line) }
        } else {
            {}
        }
    }

    private companion object {
        const val GENERATOR_LOG_TAG = "ArrowEscapeGen"

        /** Longest the system splash may stay up if the first Compose frame never comes. */
        const val SYSTEM_SPLASH_FAILSAFE_MS = 700L
    }
}
