package app.onlyshots

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
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
            system.setOnExitAnimationListener { provider ->
                val icon = provider.iconView
                val at = IntArray(2).also { icon.getLocationInWindow(it) }
                // The system draws the 240 dp icon at 1.5 times its view (a 288 dp icon over a 192 dp view), and the
                // mark is
                // 64 % of the icon (design/splash/avd.py): 0.96 of the view, in its middle.
                val side = icon.width * 0.96f
                val left = at[0] + (icon.width - side) / 2f
                val top = at[1] + (icon.height - side) / 2f
                // The app's mark goes where the system's is now, well before the swap, so not one frame shows it
                // elsewhere.
                splash.markBounds = Rect(left, top, left + side, top + side)
                val remaining =
                    (provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis -
                            SystemClock.uptimeMillis())
                        .coerceIn(0L, 1000L)
                icon.postDelayed(
                    {
                        splash.release()
                        // A frame later, so the app's mark is drawn under the system's before it goes.
                        provider.view.postOnAnimation { provider.remove() }
                    },
                    remaining,
                )
            }
        }
        // Recording survives rotation and posture changes: the activity handles config changes itself.
        setContent { OnlyShotsApp(splash) }
    }
}
