package app.onlyshots

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Recording survives rotation and posture changes: the activity handles config changes itself.
        setContent { OnlyShotsApp() }
    }
}
