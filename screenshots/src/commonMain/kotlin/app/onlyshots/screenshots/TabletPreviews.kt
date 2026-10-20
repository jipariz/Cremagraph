package app.onlyshots.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.VideoFormat
import app.onlyshots.home.HomeBody
import app.onlyshots.home.HomeUiState
import app.onlyshots.home.NextTake
import app.onlyshots.home.SavedSource
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.layout.WindowLayout
import app.onlyshots.sources.ConnectionState
import uk.co.fractalmotion.mugshot.annotations.Mugshot
import uk.co.fractalmotion.mugshot.annotations.MugshotDevice
import uk.co.fractalmotion.mugshot.annotations.MugshotDevices

/*
 * The record and edit screens on a tablet both ways up: the camera filling the window, and the editor in each format,
 * where its panel goes (beside the picture or under it) and how large the video's frame gets. Mugshot's tablet is 1280 × 800 on its side;
 * held upright it is drawn by [PortraitTablet].
 */

/**
 * An 800 × 1280 tablet held upright, drawn at 0.625 of its size so it fits Mugshot's 1280 × 800 canvas. The window
 * layout and the window's size are handed over directly: the real ones measure the canvas, which lies on its side.
 */
@Composable
private fun PortraitTablet(content: @Composable () -> Unit) = CgTheme {
    val density = LocalDensity.current
    val scaled = Density(density.density * PORTRAIT_SCALE, density.fontScale)
    val window = LocalWindowInfo.current
    val upright =
        object : WindowInfo by window {
            override val containerSize: IntSize =
                IntSize((800 * scaled.density).toInt(), (1280 * scaled.density).toInt())
        }
    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalWindowInfo provides upright,
        LocalWindowLayout provides WindowLayout(LayoutKind.Wide, splitDp = 560f),
    ) {
        Box(Modifier.size(800.dp, 1280.dp)) { content() }
    }
}

private const val PORTRAIT_SCALE = 0.625f

/** The editor on a video of [format], the graph put down so the panel shows the video's settings. */
@Composable
private fun TabletEdit(format: VideoFormat) =
    EditUi(
        sheet = null,
        video = sampleVideo.copy(format = format, overlay = OverlaySettings(rect = NormRect.default(format))),
        selected = false,
    )

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun EditUprightTall() = PortraitTablet { TabletEdit(VideoFormat.PORTRAIT_9_16) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun EditUprightSquare() = PortraitTablet { TabletEdit(VideoFormat.SQUARE_1_1) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun EditUprightWide() = PortraitTablet { TabletEdit(VideoFormat.LANDSCAPE_16_9) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun EditSidewaysTall() = Screen { TabletEdit(VideoFormat.PORTRAIT_9_16) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun EditSidewaysSquare() = Screen { TabletEdit(VideoFormat.SQUARE_1_1) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun EditSidewaysWide() = Screen { TabletEdit(VideoFormat.LANDSCAPE_16_9) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun EditUprightFeed() = PortraitTablet { TabletEdit(VideoFormat.FEED_4_5) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun EditSidewaysFeed() = Screen { TabletEdit(VideoFormat.FEED_4_5) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun RecordUpright() = PortraitTablet { Record(armedTake) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun RecordSideways() = Screen { Record(armedTake) }

/** A scale as the next take's source: its rules worded for the first drops, all of them on the panel. */
private val scaleConnected =
    HomeUiState(
        saved = SavedSource(SourceKind.SCALE, host = null, scaleId = "bookoo", scaleName = "BOOKOO_SC_U 430546"),
        connection = ConnectionState.Connected,
    )

@Composable
private fun HomeScale() =
    HomeBody(scaleConnected, {}, {}, {}, {}, {}, take = NextTake(live = Sample(0, weight = 0f, flow = 0f)))

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun HomeScaleUpright() = PortraitTablet { HomeScale() }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun HomeScaleSideways() = Screen { HomeScale() }
