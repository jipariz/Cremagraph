package app.onlyshots.style

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CoffeeScene
import app.onlyshots.core.model.NormRect
import app.onlyshots.core.model.OverlaySettings
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.style_preview_drag
import app.onlyshots.core.resources.style_preview_record
import app.onlyshots.overlay.compose.ShotOverlay
import app.onlyshots.shareBounds
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.stringResource

/**
 * 330 dp of the picture with the graph on it: the [live] picture when there is one, else a still frame of the video,
 * dragged sideways to try other frames.
 */
@Composable
internal fun StylePreview(
    style: GraphStyle,
    image: ImageBitmap?,
    live: LivePicture?,
    luma: Float,
    frameCount: Int,
    onFrame: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The whole 9:16 frame fitted to the space (a wide window), rather than the strip the graph sits on. */
    full: Boolean = false,
    sharedKey: Any? = null,
) {
    val haze = rememberHazeState()
    val drag = canDragFrames(live != null, frameCount)
    // What the picture is and what dragging it does: inside the frame when the whole frame shows, else over the
    // strip.
    val caption =
        @Composable {
            PreviewCaptionLabel(
                when (previewCaption(live != null, frameCount)) {
                    PreviewCaption.LIVE -> live?.caption.orEmpty()
                    PreviewCaption.DRAG -> stringResource(Res.string.style_preview_drag)
                    PreviewCaption.RECORD -> stringResource(Res.string.style_preview_record)
                }
            )
        }
    // The drag outlives a recomposition (more frames loaded): it steps through the frames as they are now.
    val frameBy by rememberUpdatedState(onFrame)
    BoxWithConstraints(
        (if (full) modifier.fillMaxSize() else modifier.fillMaxWidth().height(PREVIEW_HEIGHT))
            // Grows out of the tile it was opened from. Drawn in its own layer: the picture under it may be a camera
            // or a video, and the graph's blur needs its source.
            .then(if (sharedKey != null) Modifier.shareBounds(sharedKey, overlay = false) else Modifier)
            .clipToBounds()
            .drawWithContent {
                drawContent()
                if (!full) drawBottomHairline()
            }
            .pointerInput(drag) {
                if (!drag) return@pointerInput
                var carried = 0f
                detectHorizontalDragGestures(onDragEnd = { carried = 0f }) { _, dx ->
                    val moved = dragFrames(carried, dx, DRAG_STEP.toPx())
                    carried = moved.remainder
                    if (moved.steps != 0) frameBy(moved.steps)
                }
            }
    ) {
        val frameH = maxWidth * 16f / 9f
        // The frame is 9:16; the strip shows the part the graph sits on, as in Figma.
        Box(
            if (full)
                Modifier.align(Alignment.Center)
                    .fillMaxHeight()
                    .padding(CgSpace.S24)
                    .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                    .border(CgStroke.Hairline, CgColors.Line.Hairline)
                    .padding(CgStroke.Hairline)
                    .clipToBounds()
            else
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(maxWidth, frameH)
                    .offset(y = -PREVIEW_SHIFT)
        ) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                when {
                    live != null -> live.content(Modifier.fillMaxSize().clipToBounds())
                    image != null -> Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else -> CoffeeScene(Modifier.fillMaxSize(), progress = SCENE_PROGRESS)
                }
            }
            ShotOverlay(
                shot = SampleShots.zer0,
                videoTimeUs = PREVIEW_AT_US,
                shotStartUs = 0,
                settings = OverlaySettings(styleId = style.id, rect = PreviewRect),
                style = style,
                title = "${style.name} · Zer0",
                fonts = CgTheme.overlayFonts,
                modifier = Modifier.fillMaxSize(),
                hazeState = haze,
                backdropLuma = luma,
            )
            if (full) caption()
        }
        if (!full) caption()
    }
}

/** What the picture is, on a tag over it. */
@Composable
private fun PreviewCaptionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.caps(),
        style = CgTheme.type.labelStrong,
        modifier =
            modifier
                .padding(CgSpace.S8)
                .background(CgColors.Surface.Base.copy(alpha = CAPTION_ALPHA))
                .border(CgStroke.Hairline, CgColors.Line.Hairline)
                .padding(horizontal = CgSpace.S8, vertical = CgSpace.S4),
        color = CgColors.Text.Primary,
    )
}

/** Where the graph sits in the 390 × 693 frame: 14 px in, under the shift, 170 tall — Figma's layout. */
internal val PreviewRect = NormRect(14f / 390f, (120f + 130f) / 693f, 362f / 390f, 170f / 693f)

private val PREVIEW_HEIGHT = 330.dp

/** How far the frame is lifted in the strip, so the graph's part of it shows. */
private val PREVIEW_SHIFT = 120.dp

/** A drag this far sideways moves one frame. */
private val DRAG_STEP = 24.dp

/** The moment of the sample shot the preview shows: well into the shot, every curve drawn. */
private const val PREVIEW_AT_US = 24_000_000L

/** How far the drawn scene's shot has poured. */
private const val SCENE_PROGRESS = 0.7f

private const val CAPTION_ALPHA = 0.85f
