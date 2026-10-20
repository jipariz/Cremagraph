package app.onlyshots

import androidx.compose.runtime.Composable
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Quality
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.machineName
import app.onlyshots.core.model.overlayTitle
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.source_scale
import org.jetbrains.compose.resources.stringResource

/** An export quality as the export sheet names it. */
val Quality.label: String
    get() =
        when (this) {
            Quality.HD_1080 -> "1080p"
            Quality.UHD_4K -> "4K"
        }

/** The machine's name as the screens write it; a scale-only shot says "scale" in the user's language. */
@Composable fun machineLabel(kind: SourceKind): String = machineName(kind, stringResource(Res.string.source_scale))

/** The graph's default or own title (see [overlayTitle]), in composition. */
@Composable
fun overlayTitleText(shot: Shot, overlay: OverlaySettings): String =
    overlayTitle(shot, overlay, stringResource(Res.string.source_scale))
