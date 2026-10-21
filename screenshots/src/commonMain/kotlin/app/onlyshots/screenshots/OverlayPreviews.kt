package app.onlyshots.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.CoffeeScene
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.TimeRange
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.overlay.GraphStyle
import app.onlyshots.overlay.GraphStyles
import app.onlyshots.overlay.compose.ShotOverlay
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import uk.co.fractalmotion.mugshot.annotations.Mugshot
import uk.co.fractalmotion.mugshot.annotations.MugshotShrink

/**
 * Every built-in graph style at the start of the shot, mid-shot and after it ended, over the drawn espresso scene so
 * the glass and blur styles have something behind them. Two more cover a shot with a dropped stretch and a title too
 * long for its line.
 *
 * The shot is `SampleShots.zer0`, the fixture the scene tests use, and the overlay keeps its default rect, so a style
 * change is the only thing that can move these pixels. The frame is 9:16 at the preview device's width; the rect is
 * normalized, so the proportions are the app's whatever that width is.
 */
@Composable
private fun Frame(
    style: GraphStyle,
    atUs: Long,
    shot: Shot = SampleShots.zer0,
    title: String = "Zer0 · Gaggiuino",
) {
    CgTheme {
        val haze = rememberHazeState()
        Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).background(CgColors.Surface.Base)) {
            CoffeeScene(Modifier.fillMaxSize().hazeSource(haze), progress = (atUs / 40_000_000f).coerceIn(0f, 1f))
            ShotOverlay(
                shot = shot,
                videoTimeUs = atUs,
                shotStartUs = 0,
                settings = OverlaySettings(styleId = style.id, rect = NormRect.Default),
                style = style,
                title = title,
                fonts = CgTheme.overlayFonts,
                modifier = Modifier.fillMaxSize(),
                hazeState = haze,
            )
        }
    }
}

/** Styles are addressed by id, so a renamed style fails the build instead of a golden. */
private fun style(id: String): GraphStyle = GraphStyles.byId(id) ?: error("no style $id")

private const val START = 0L
private const val MID = 12_000_000L
/** zer0 runs 40 s, so this is past its end: the finished graph. */
private const val AFTER = 45_000_000L

/** The connection dropped from 14 s to 18 s: every curve breaks there instead of drawing a line across. */
@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun GaggiuinoGap() =
    Frame(
        style("gaggiuino"),
        MID * 2,
        shot = SampleShots.zer0.copy(gaps = listOf(TimeRange(14_000_000L, 18_000_000L))),
    )

/** A long profile name: the title is cut with an ellipsis and the legend keeps its room. */
@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun GaggiuinoLongTitle() =
    Frame(style("gaggiuino"), MID, title = "Londinium declining, 9 bar to 6 over the shot · Gaggiuino")

@Mugshot @MugshotShrink @Preview @Composable internal fun InstrumentStart() = Frame(style("instrument"), START)

@Mugshot @MugshotShrink @Preview @Composable internal fun InstrumentMid() = Frame(style("instrument"), MID)

@Mugshot @MugshotShrink @Preview @Composable internal fun InstrumentAfter() = Frame(style("instrument"), AFTER)

@Mugshot @MugshotShrink @Preview @Composable internal fun GaggiuinoStart() = Frame(style("gaggiuino"), START)

@Mugshot @MugshotShrink @Preview @Composable internal fun GaggiuinoMid() = Frame(style("gaggiuino"), MID)

@Mugshot @MugshotShrink @Preview @Composable internal fun GaggiuinoAfter() = Frame(style("gaggiuino"), AFTER)

@Mugshot @MugshotShrink @Preview @Composable internal fun MetroStart() = Frame(style("metro"), START)

@Mugshot @MugshotShrink @Preview @Composable internal fun MetroMid() = Frame(style("metro"), MID)

@Mugshot @MugshotShrink @Preview @Composable internal fun MetroAfter() = Frame(style("metro"), AFTER)

@Mugshot @MugshotShrink @Preview @Composable internal fun AeroStart() = Frame(style("aero"), START)

@Mugshot @MugshotShrink @Preview @Composable internal fun AeroMid() = Frame(style("aero"), MID)

@Mugshot @MugshotShrink @Preview @Composable internal fun AeroAfter() = Frame(style("aero"), AFTER)

@Mugshot @MugshotShrink @Preview @Composable internal fun MinimalStart() = Frame(style("minimal"), START)

@Mugshot @MugshotShrink @Preview @Composable internal fun MinimalMid() = Frame(style("minimal"), MID)

@Mugshot @MugshotShrink @Preview @Composable internal fun MinimalAfter() = Frame(style("minimal"), AFTER)

@Mugshot @MugshotShrink @Preview @Composable internal fun LiquidGlassStart() = Frame(style("liquidglass"), START)

@Mugshot @MugshotShrink @Preview @Composable internal fun LiquidGlassMid() = Frame(style("liquidglass"), MID)

@Mugshot @MugshotShrink @Preview @Composable internal fun LiquidGlassAfter() = Frame(style("liquidglass"), AFTER)
