package app.onlyshots

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_desktop_header_note
import app.onlyshots.core.resources.core_desktop_videos_note
import app.onlyshots.core.resources.core_desktop_window_title
import app.onlyshots.di.initKoin
import app.onlyshots.editor.EditorApp
import app.onlyshots.home.MachineShotsScreen
import app.onlyshots.open.OpenHost
import java.awt.Taskbar
import javax.imageio.ImageIO
import org.jetbrains.compose.resources.stringResource

/**
 * The desktop editor, like the web one: pick a video and its shot (a file, or from the machine), place the graph,
 * export. FFmpeg plays and exports. `./gradlew :app:desktopApp:run`, or `:app:desktopApp:hotRun --auto` for Compose Hot
 * Reload while working on the UI.
 */
fun main() {
    DevTools.enabled = true
    initKoin()
    // The app icon (design/logo/kit/desktop): the window's on Windows and Linux; macOS windows have none, so it goes
    // to the Dock too, which an unpackaged `:app:desktopApp:run` would otherwise show as Java's.
    val icon = ImageIO.read(checkNotNull(object {}.javaClass.getResource("/onlyshots-icon.png")))
    if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
        Taskbar.getTaskbar().iconImage = icon
    }
    val windowIcon = BitmapPainter(icon.toComposeImageBitmap())
    // The whole splash plays in the window as it opens, while the library is read.
    val splash = SplashState(held = false)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.core_desktop_window_title),
            icon = windowIcon,
            state = rememberWindowState(width = 1280.dp, height = 860.dp),
        ) {
            // The desktop reaches machines on the network, so a shot can come straight from one.
            EditorApp(
                machineShots = { onPicked, onBack -> MachineShotsScreen(onPicked, onBack) },
                host =
                    OpenHost(
                        headerNote = stringResource(Res.string.core_desktop_header_note),
                        videosNote = stringResource(Res.string.core_desktop_videos_note),
                    ),
                splash = splash,
            )
        }
    }
}
