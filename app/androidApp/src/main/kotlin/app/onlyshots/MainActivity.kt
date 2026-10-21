package app.onlyshots

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.geometry.Rect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.onlyshots.di.initKoin
import org.koin.android.ext.koin.androidContext

class OnlyShotsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DevTools.enabled = BuildConfig.DEBUG
        initKoin { androidContext(this@OnlyShotsApplication) }
    }
}

/** Longest the app's splash waits for the system's: its icon animation is about a second, the cold start less. */
private const val SPLASH_FALLBACK_MS = 3_000L

/**
 * Where the system splash's mark is, in the window: the system draws the 240 dp icon at 1.5 times its view (a 288 dp
 * icon over a 192 dp view), and the mark is 64 % of the icon (design/splash/avd.py), so 0.96 of the view, in its
 * middle. Null when the icon was never laid out (the screen was off): the app's splash then centres its own.
 */
private fun markBounds(icon: View): Rect? {
    if (icon.width <= 0 || icon.height <= 0) return null
    val at = IntArray(2).also { icon.getLocationInWindow(it) }
    val side = icon.width * 0.96f
    val left = at[0] + (icon.width - side) / 2f
    val top = at[1] + (icon.height - side) / 2f
    return Rect(left, top, left + side, top + side)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // First: it swaps the starting theme for the app's before anything (edge to edge) builds the window from it.
        val system = installSplashScreen()
        // The app is dark whatever the system's theme: light icons, and no light scrim behind three-button navigation.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // The system splash has drawn the shot; the app holds the finished mark where it was and carries on with the
        // pulse and the hand-off once the system's animation is over (never cut short, never waited on beyond it).
        val splash = if (savedInstanceState == null && !SplashOnce.played) SplashState.afterSystemSplash() else null
        if (splash != null) {
            // The main thread's own queue, not the splash icon's: an activity started with the screen off gets a
            // splash view that is never laid out, and a runnable posted to it never ran, which left the app's splash
            // over the first screen for good.
            val main = Handler(Looper.getMainLooper())
            system.setOnExitAnimationListener { provider ->
                markBounds(provider.iconView)?.let { splash.markBounds = it }
                val remaining =
                    (provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis -
                            SystemClock.uptimeMillis())
                        .coerceIn(0L, 1000L)
                main.postDelayed(
                    {
                        splash.release()
                        // A frame later, so the app's mark is drawn under the system's before it goes. A view that
                        // is not on screen gets no frames: it goes at once.
                        val view = provider.view
                        if (view.isShown) view.postOnAnimation { provider.remove() } else provider.remove()
                    },
                    remaining,
                )
            }
            // The system hands its splash over only if it showed one. Whatever happened, the app shows itself.
            main.postDelayed({ if (!splash.released) splash.release() }, SPLASH_FALLBACK_MS)
        }
        // Recording survives rotation and posture changes: the activity handles config changes itself.
        setContent { OnlyShotsApp(splash) }
    }
}
