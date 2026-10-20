package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A drawn espresso shot in 9:16 — group head, portafilter, two streams, a glass cup on the drip tray — as in Figma's
 * camera preview. It stands in for the video where there is none: the style editor before the first recording, and the
 * screenshot tests, so glass and blur styles have something to blur.
 *
 * [progress] 0..1 fills the cup, so frames of it differ like frames of a real shot.
 */
@Composable
fun CoffeeScene(modifier: Modifier = Modifier, progress: Float = 0.5f) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun x(v: Float) = v / 390f * w
        fun y(v: Float) = v / 693f * h
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3A2C26), Color(0xFF2A1F1B), Color(0xFF1C1512))))
        // Back wall light.
        drawCircle(Color(0x22FFE2C0), radius = x(220f), center = Offset(x(250f), y(140f)))
        // Group head and portafilter.
        drawRoundRect(Color(0xFFD9D6D2), Offset(x(120f), y(0f)), Size(x(150f), y(40f)), CornerRadius(x(8f)))
        drawRoundRect(Color(0xFF2B2421), Offset(x(80f), y(40f)), Size(x(240f), y(34f)), CornerRadius(x(17f)))
        drawRoundRect(Color(0xFF4A3F3A), Offset(x(96f), y(50f)), Size(x(200f), y(4f)), CornerRadius(x(2f)))
        drawRoundRect(Color(0xFFCFCAC5), Offset(x(172f), y(74f)), Size(x(18f), y(22f)), CornerRadius(x(2f)))
        drawRoundRect(Color(0xFFCFCAC5), Offset(x(200f), y(74f)), Size(x(18f), y(22f)), CornerRadius(x(2f)))
        // Streams.
        val stream = Color(0xFF8A4A22)
        drawLine(stream, Offset(x(181f), y(96f)), Offset(x(177f), y(330f)), x(4f))
        drawLine(stream, Offset(x(209f), y(96f)), Offset(x(213f), y(330f)), x(4f))
        // Glass cup with the shot rising in it.
        val cupTop = y(300f)
        val cupBottom = y(420f)
        val fillTop = cupBottom - (cupBottom - cupTop - y(10f)) * progress.coerceIn(0.05f, 1f)
        drawRoundRect(
            Color(0xFF5A3315),
            Offset(x(142f), fillTop),
            Size(x(106f), cupBottom - fillTop),
            CornerRadius(x(6f)),
        )
        drawRoundRect(Color(0xFFC08A52), Offset(x(142f), fillTop), Size(x(106f), y(8f)), CornerRadius(x(4f)))
        drawRoundRect(
            Color(0x55FFFFFF),
            Offset(x(138f), cupTop),
            Size(x(114f), cupBottom - cupTop),
            CornerRadius(x(10f)),
            style = Stroke(x(2f)),
        )
        drawRoundRect(
            Color(0x18FFFFFF),
            Offset(x(138f), cupTop),
            Size(x(114f), cupBottom - cupTop),
            CornerRadius(x(10f)),
        )
        // Drip tray.
        drawRect(Color(0xFF16110F), Offset(0f, y(420f)), Size(w, h - y(420f)))
        drawRoundRect(Color(0xFF2E2622), Offset(x(40f), y(436f)), Size(x(310f), y(6f)), CornerRadius(x(3f)))
    }
}
