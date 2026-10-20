package app.onlyshots

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        // The app is dark whatever the system's theme: light icons, and no light scrim behind three-button navigation.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Recording survives rotation and posture changes: the activity handles config changes itself.
        setContent { OnlyShotsApp() }
    }
}
