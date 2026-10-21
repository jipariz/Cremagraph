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

/** The splash's key frames, as design/splash/filmstrip.png has them: what the app draws at each moment. */
@Composable
private fun Frame(t: Float) =
    Box(Modifier.size(220.dp).background(CgColors.Surface.Base), contentAlignment = Alignment.Center) {
        SplashMark(Splash.pose(t), Modifier.size(SplashMarkSize))
    }

@Mugshot @MugshotShrink @Preview @Composable internal fun SplashCornersFocus() = Frame(150f)

@Mugshot @MugshotShrink @Preview @Composable internal fun SplashPlayheadIn() = Frame(420f)

@Mugshot @MugshotShrink @Preview @Composable internal fun SplashRecording() = Frame(700f)

@Mugshot @MugshotShrink @Preview @Composable internal fun SplashShotDrawn() = Frame(1000f)

@Mugshot @MugshotShrink @Preview @Composable internal fun SplashPulse() = Frame(1180f)
