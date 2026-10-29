package app.onlyshots.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.onlyshots.camera.CameraError
import app.onlyshots.camera.CameraState
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.component.Banner
import app.onlyshots.core.designsystem.component.BannerKind
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.camera_no_rear
import app.onlyshots.core.resources.camera_no_space
import app.onlyshots.core.resources.camera_recording_failed
import app.onlyshots.core.resources.text
import org.jetbrains.compose.resources.stringResource

/** Why the camera or the last take failed, over the picture. */
@Composable
internal fun Banners(camera: CameraState, saveError: UiText?) {
    WarningBanner(camera.error?.text())
    WarningBanner(saveError?.text())
}

/** A warning that opens over the picture's edge and closes up again, still saying what it said while it goes. */
@Composable
private fun WarningBanner(text: String?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(text) }
    if (text != null) last = text
    AnimatedVisibility(
        text != null,
        modifier,
        enter = CgMotion.reveal,
        exit = CgMotion.conceal,
    ) {
        last?.let { Banner(it, BannerKind.Warning) }
    }
}

/** The camera's error as the banner says it. */
@Composable
private fun CameraError.text(): String =
    when (this) {
        CameraError.NoRearCamera -> stringResource(Res.string.camera_no_rear)
        CameraError.NoSpace -> stringResource(Res.string.camera_no_space)
        is CameraError.RecordingFailed -> detail ?: stringResource(Res.string.camera_recording_failed)
    }

/** A small key that slides out of its row's edge and back into it, rather than appearing and vanishing. */
@Composable
internal fun KeyIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = expandHorizontally(CgMotion.sizeSpec, expandFrom = Alignment.End),
        exit = shrinkHorizontally(CgMotion.sizeSpec, shrinkTowards = Alignment.End),
    ) {
        content()
    }
}
