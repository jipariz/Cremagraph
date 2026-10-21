package app.onlyshots.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.onlyshots.SplashMarkSize
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.component.Splash
import app.onlyshots.core.designsystem.component.SplashMark
import uk.co.fractalmotion.mugshot.annotations.Mugshot
import uk.co.fractalmotion.mugshot.annotations.MugshotShrink

/**
 * The splash's key frames, as design/splash/filmstrip.png has them: the tile's wipe, the landing, the plot, the plotted
 * mark and the tick.
 */
@Composable
private fun Frame(t: Float) =
    Box(Modifier.size(220.dp).background(CgColors.Surface.Base), contentAlignment = Alignment.Center) {
        SplashMark(Splash.pose(t), Modifier.size(SplashMarkSize))
    }

@Mugshot @MugshotShrink @Preview @Composable internal fun SplashTileWipe() = Frame(120f)

@Mugshot @MugshotShrink @Preview @Composable internal fun SplashPenLands() = Frame(300f)

@Mugshot @MugshotShrink @Preview @Composable internal fun SplashPlotting() = Frame(760f)

@Mugshot @MugshotShrink @Preview @Composable internal fun SplashPlotted() = Frame(1000f)

@Mugshot @MugshotShrink @Preview @Composable internal fun SplashTick() = Frame(1220f)
