package app.onlyshots.edit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.CgTypography
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_timeline_description
import app.onlyshots.core.resources.edit_timeline_seconds
import app.onlyshots.core.resources.edit_timeline_trim
import app.onlyshots.core.resources.edit_timeline_zoom
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource

/** What the timeline asks the screen to do; trims and the flag carry `push`, so one drag is one undo step. */
class TimelineActions(
    val seek: (us: Long) -> Unit,
    val trim: (inUs: Long, outUs: Long, push: Boolean) -> Unit,
    val shotStart: (us: Long, push: Boolean) -> Unit,
    /** A multiple of the fitted scale, or null to fit the clip again. */
    val zoom: (factor: Float?) -> Unit,
)

/**
 * The web editor's phone timeline, 128 dp: the trim and the clock, the whole clip in one line with the shot's span, a
 * ruler, the filmstrip with the trim handles, the sound with the pump start, the shot flag and the playhead.
 *
 * Drag a trim handle or the flag to move it (a handle snaps to the playhead and the shot's start and end, the flag to
 * the playhead and the pump start); the header says when the trim cuts into the shot. Drag anywhere else to scrub; tap
 * the overview line to jump; pinch to zoom, which keeps the playhead in the middle.
 *
 * [compact]: only the filmstrip with its handles, flag and playhead, 60 dp (the phone, while the graph is picked).
 */
@Composable
fun EditTimeline(
    item: VideoItem,
    /** Where playback is, read where it is used: it changes every frame as the video plays. */
    playheadUs: () -> Long,
    zoomFactor: Float?,
    thumbnails: List<ImageBitmap>,
    waveform: FloatArray?,
    pumpUs: Long?,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    // While the frames are being made, a light sweeps across the empty strip. Only then: an endless animation would
    // keep the canvas drawing, and the screenshots from settling.
    val sweep =
        if (loading)
            rememberInfiniteTransition(label = "strip")
                .animateFloat(
                    0f,
                    1f,
                    infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing)),
                    label = "sweep",
                )
        else null
    // Every label of a frame (header, ruler ticks) fits, so none is laid out again on the next.
    val text = rememberTextMeasurer(cacheSize = TEXT_CACHE)
    val haptic = LocalHapticFeedback.current
    val type = CgTheme.type
    // What is drawn eases to a jump (a tap on the overview, "start here", a zoom key) and follows playback and the
    // finger exactly: a drag or a pinch sets [moving] for as long as it lasts.
    var moving by remember { mutableStateOf(false) }
    val playheadNow by rememberUpdatedState(playheadUs)
    val shownPlayhead = remember { Animatable(playheadUs().toFloat()) }
    // One collector for the whole life of the timeline, rather than an effect restarted on every frame of playback; a
    // new position cancels an ease in progress, as the restart did.
    LaunchedEffect(Unit) {
        snapshotFlow { playheadNow() }
            .collectLatest {
                val target = it.toFloat()
                if (moving || abs(target - shownPlayhead.value) < JUMP_US) shownPlayhead.snapTo(target)
                else shownPlayhead.animateTo(target, tween(CgMotion.MEDIUM, easing = CgMotion.Mechanical))
            }
    }
    val shownZoom = remember { Animatable(zoomFactor ?: 1f) }
    LaunchedEffect(zoomFactor) {
        val target = zoomFactor ?: 1f
        if (moving) shownZoom.snapTo(target) else shownZoom.animateTo(target, CgMotion.settle())
    }
    val current by rememberUpdatedState(item)
    val zoom by rememberUpdatedState(zoomFactor)
    val act by rememberUpdatedState(actions)
    // Compact, the strip is drawn [COMPACT_SHIFT] higher: gestures read where they would land on the full timeline.
    val shift by rememberUpdatedState(if (compact) COMPACT_SHIFT else 0)
    // The pump start arrives once the sound is read (and goes when the start is locked): snap to the one there now.
    val pump by rememberUpdatedState(pumpUs)
    // Drawn every frame of playback: the header's styles made once, the playhead's triangle refilled in place.
    val headerStyles = remember(type) { HeaderStyles(type) }
    // The shimmer's light, made once per density and moved where it is drawn, rather than made again every frame.
    val density = LocalDensity.current
    val sweepBrush =
        remember(density) {
            Brush.horizontalGradient(
                listOf(Color.Transparent, CgColors.Text.Primary.copy(alpha = 0.12f), Color.Transparent),
                startX = 0f,
                endX = with(density) { SHIMMER_BAND.dp.toPx() },
            )
        }
    val playheadTri = remember { Path() }
    val description = stringResource(Res.string.edit_timeline_description)
    val words =
        HeaderWords(
            trim = stringResource(Res.string.edit_timeline_trim, clock(item.trimInUs), clock(item.trimOutUs)).caps(),
            zoom = stringResource(Res.string.edit_timeline_zoom).caps(),
            length = stringResource(Res.string.edit_timeline_seconds, seconds(item.trimmedDurationUs)),
            cut = TimelineMath.shotCut(item)?.let { stringResource(it).caps() },
        )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(if (compact) COMPACT_HEIGHT.dp else HEIGHT.dp)
            .clipToBounds()
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pad = PAD.dp.toPx()
                    fun view() = timelineView(size.width.toFloat(), current.durationUs, playheadNow(), pad, zoom)
                    val y = down.position.y + shift.dp.toPx()
                    val inOverview = y < RULER_TOP.dp.toPx()
                    if (inOverview && y < (OVERVIEW - 8).dp.toPx()) return@awaitEachGesture
                    fun overviewUs(x: Float): Long =
                        (((x - pad) / (size.width - 2 * pad)) * current.durationUs)
                            .toLong()
                            .coerceIn(0, current.durationUs)
                    fun clampToTrim(us: Long) = us.coerceIn(current.trimInUs, current.trimOutUs)

                    val grip =
                        if (inOverview) TimelineHandle.PLAYHEAD
                        else
                            TimelineMath.hitHandle(
                                down.position.x,
                                current,
                                view(),
                                GRAB.dp.toPx(),
                                onFlagTab = y in RULER_TOP.dp.toPx()..STRIP.dp.toPx() + 8.dp.toPx(),
                            )
                    // A handle's drag keeps the strip as it was at the press: zoomed, the strip follows the playhead,
                    // and the playhead it pushes must not slide the strip under the finger.
                    val pressView = view()
                    var push = true
                    var lastX = down.position.x
                    // A snap is felt, not shown: a tick as the handle catches a target.
                    var lastSnap: SnapTarget? = null
                    fun snapped(target: SnapTarget?) {
                        if (target != null && target != lastSnap) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        lastSnap = target
                    }
                    fun apply(x: Float) {
                        val v = view()
                        val pull = v.usFor(SNAP.dp.toPx())
                        when (grip) {
                            TimelineHandle.PLAYHEAD ->
                                act.seek(
                                    clampToTrim(
                                        when {
                                            inOverview -> overviewUs(x)
                                            v.zoomed -> playheadNow() - v.usFor(x - lastX)
                                            else -> v.us(x)
                                        }
                                    )
                                )
                            TimelineHandle.TRIM_IN,
                            TimelineHandle.TRIM_OUT -> {
                                // Not the playhead: the handle pushes it, and catching on it made the handle stick.
                                val targets =
                                    listOf(
                                        SnapTarget.SHOT_START to current.shotStartInVideoUs,
                                        SnapTarget.SHOT_END to current.shotEndInVideoUs,
                                    )
                                val (us, label) = TimelineMath.snap(pressView.us(x), targets, pull)
                                val inUs = if (grip == TimelineHandle.TRIM_IN) us else current.trimInUs
                                val outUs = if (grip == TimelineHandle.TRIM_OUT) us else current.trimOutUs
                                val next = current.trimmed(inUs, outUs)
                                // A locked start stops the handle: felt as it is reached.
                                val stopped =
                                    current.syncLocked &&
                                        (next.trimInUs != inUs.coerceAtLeast(0) && grip == TimelineHandle.TRIM_IN ||
                                            next.trimOutUs != outUs.coerceAtMost(current.durationUs) &&
                                                grip == TimelineHandle.TRIM_OUT)
                                snapped(if (stopped) SnapTarget.SHOT_START else label)
                                act.trim(inUs, outUs, push)
                                // The playhead rides the handle in the same frame, not after the trim comes back.
                                TimelineMath.pushedPlayhead(next, playheadNow())?.let(act.seek)
                                push = false
                            }
                            TimelineHandle.SHOT_FLAG -> {
                                val (us, label) =
                                    TimelineMath.snap(
                                        pressView.us(x),
                                        listOf(
                                            SnapTarget.PUMP_START to pump,
                                            SnapTarget.PLAYHEAD to playheadNow(),
                                        ),
                                        pull,
                                    )
                                snapped(label)
                                act.shotStart(us, push)
                                push = false
                            }
                        }
                        lastX = x
                    }
                    if (inOverview || grip == TimelineHandle.PLAYHEAD && !view().zoomed) apply(down.position.x)

                    var pinchStart: Float? = null
                    var pinchScale = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        moving = true
                        if (pressed.size >= 2) {
                            // A second finger turns the gesture into a zoom and drops the drag.
                            val d = (pressed[0].position - pressed[1].position).getDistance()
                            if (pinchStart == null) {
                                pinchStart = d
                                pinchScale = view().scale
                            } else if (pinchStart > 0f) {
                                val fit = view().fitScale
                                val s = (pinchScale * d / pinchStart).coerceIn(fit, TimelineMath.MAX_PX_PER_SECOND)
                                act.zoom(if (s <= fit * 1.05f) null else s / fit)
                            }
                            pressed.forEach(PointerInputChange::consume)
                            continue
                        }
                        if (pinchStart != null) continue
                        val change = pressed.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        apply(change.position.x)
                    }
                    moving = false
                }
            }
    ) {
        val pad = PAD.dp.toPx()
        val v =
            timelineView(
                size.width,
                item.durationUs,
                shownPlayhead.value.toLong(),
                pad,
                shownZoom.value.takeIf { it > 1.001f },
            )
        if (compact) {
            translate(top = -COMPACT_SHIFT.dp.toPx()) {
                strip(item, v, pad, thumbnails, sweep?.value, sweepBrush)
                flag(item, v)
                playheadMark(v, playheadTri)
            }
        } else {
            header(text, headerStyles, words, v)
            overview(item, v, pad)
            ruler(text, headerStyles.ruler, v, pad)
            strip(item, v, pad, thumbnails, sweep?.value, sweepBrush)
            wave(v, pad, waveform, pumpUs, item.durationUs)
            flag(item, v)
            playheadMark(v, playheadTri)
        }
    }
}

/** The header's words, put into words in composition for the canvas to draw. */
private class HeaderWords(val trim: String, val zoom: String, val length: String, val cut: String?)

/** The header's text styles, made once per typography rather than on every frame it is drawn. */
private class HeaderStyles(type: CgTypography) {
    val style = type.data.copy(color = CgColors.Text.Primary)
    val dim = style.copy(color = CgColors.Text.Dim)
    val warning = style.copy(color = CgColors.Status.Warning)
    val clock = style.copy(color = CgColors.Text.Primary, fontWeight = FontWeight.SemiBold)
    /** The ruler's labels: 10 sp, the smallest the app sets. */
    val ruler = type.micro.copy(color = CgColors.Text.Dim)
}

private fun DrawScope.header(
    text: TextMeasurer,
    styles: HeaderStyles,
    words: HeaderWords,
    v: TimelineView,
) {
    // The trim in Mono capitals, in full; the length beside it dimmer.
    val style = styles.style
    val dim = styles.dim
    val left =
        if (v.zoomed) "${clock(v.us(PAD.dp.toPx()))} – ${clock(v.us(size.width - PAD.dp.toPx()))}" else words.trim
    // A trim that cuts into the shot cuts the graph too: said in place of the length, where it is seen while dragging.
    val cut = words.cut.takeUnless { v.zoomed }
    val right =
        when {
            v.zoomed -> "${words.zoom} ${(v.scale / v.fitScale * 10).toInt() / 10f}×"
            cut != null -> cut
            else -> words.length
        }
    val top = (HEADER_BASELINE - 13).dp.toPx()
    drawText(text, left, Offset(PAD.dp.toPx(), top), style)
    val r = text.measure(right, if (cut != null) styles.warning else dim)
    drawText(r, topLeft = Offset(size.width - PAD.dp.toPx() - r.size.width, top))
    val c = text.measure(clock(v.playheadUs), styles.clock)
    drawText(c, topLeft = Offset((size.width - c.size.width) / 2f, top))
}

/** The whole clip in one 4 dp line: the trim a hairline lighter, the shot's span in orange. */
private fun DrawScope.overview(item: VideoItem, v: TimelineView, pad: Float) {
    val y = OVERVIEW.dp.toPx()
    val h = 4.dp.toPx()
    val w = size.width - 2 * pad
    fun x(us: Long) = pad + us.toFloat() / item.durationUs.coerceAtLeast(1) * w
    drawRect(CgColors.Line.Divider, Offset(pad, y), Size(w, h))
    drawRect(CgColors.Line.Hairline, Offset(x(item.trimInUs), y), Size(x(item.trimOutUs) - x(item.trimInUs), h))
    val start = item.shotStartInVideoUs
    val shot = item.shot
    if (start != null && shot != null) {
        val a = x(start.coerceIn(0, item.durationUs))
        val b = x((start + shot.durationUs).coerceIn(0, item.durationUs))
        if (b > a) drawRect(CgColors.Brand.Signal, Offset(a, y), Size(b - a, h))
    }
    if (v.zoomed) {
        val a = x(v.us(pad))
        val b = x(v.us(size.width - pad))
        drawRect(
            CgColors.Text.Primary,
            Offset(a, y - 1.dp.toPx()),
            Size((b - a).coerceAtLeast(6.dp.toPx()), h + 2.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
        )
    }
}

private fun DrawScope.ruler(
    text: TextMeasurer,
    style: TextStyle,
    v: TimelineView,
    pad: Float,
) {
    val step = TimelineMath.rulerStep(v.scale, 60.dp.toPx())
    val top = RULER_TOP.dp.toPx()
    // Marks are at least 60 dp apart, so the labels never touch.
    val stepUs = (step * 1e6f).toLong()
    var t = (v.us(pad) / stepUs) * stepUs
    val end = v.us(size.width - pad)
    while (t <= end) {
        val x = v.x(t)
        if (x >= pad && x <= size.width - pad) {
            drawRect(CgColors.Text.Dim, Offset(x, top), Size(1.dp.toPx(), 5.dp.toPx()))
            drawText(text, rulerClock(t), Offset(x + 3.dp.toPx(), top - 1.dp.toPx()), style)
        }
        for (m in 1..4) {
            val mx = v.x(t + stepUs * m / 5)
            if (mx >= pad && mx <= size.width - pad) {
                drawRect(CgColors.Line.Divider, Offset(mx, top), Size(1.dp.toPx(), 2.dp.toPx()))
            }
        }
        t += stepUs
    }
}

/**
 * The filmstrip, dimmed outside the trim, with a handle at each end of it. While its frames are being made it shimmers,
 * a light at [sweep] (0 to 1) of its way across, like a shine over metal.
 */
private fun DrawScope.strip(
    item: VideoItem,
    v: TimelineView,
    pad: Float,
    thumbnails: List<ImageBitmap>,
    sweep: Float?,
    /** The light, from 0 to its band's width: moved to where the sweep is. */
    sweepBrush: Brush,
) {
    val top = STRIP.dp.toPx()
    val h = STRIP_H.dp.toPx()
    clipRect(pad, top, size.width - pad, top + h) {
        drawRect(CgColors.Surface.Raised, Offset(pad, top), Size(size.width - 2 * pad, h))
        val x0 = v.x(0)
        val cell = (v.x(item.durationUs) - x0) / thumbnails.size.coerceAtLeast(1)
        if (sweep != null) {
            // Over the clip as it lies in the timeline, zoomed or not.
            val from = maxOf(pad, x0)
            val to = minOf(size.width - pad, v.x(item.durationUs))
            if (to > from) {
                val band = SHIMMER_BAND.dp.toPx()
                val at = pad - band + sweep * (size.width - 2 * pad + band)
                translate(left = at) { drawRect(sweepBrush, Offset(from - at, top), Size(to - from, h)) }
            }
        }
        if (thumbnails.isNotEmpty()) {
            thumbnails.forEachIndexed { i, image ->
                val left = x0 + i * cell
                if (left + cell >= pad && left <= size.width - pad) {
                    drawImage(
                        image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize((cell + 1).toInt().coerceAtLeast(1), h.toInt()),
                    )
                }
            }
        }
        val scrim = CgColors.Surface.Base.copy(alpha = 0.72f)
        drawRect(scrim, Offset(pad, top), Size((v.x(item.trimInUs) - pad).coerceAtLeast(0f), h))
        val out = v.x(item.trimOutUs)
        drawRect(scrim, Offset(out, top), Size((size.width - pad - out).coerceAtLeast(0f), h))
        // The two handles, the in one inside its edge and the out one inside its own (no list made per frame).
        fun handle(us: Long, inSide: Boolean) {
            val w = HANDLE_W.dp.toPx()
            val x = v.x(us) - if (inSide) 0f else w
            drawRect(CgColors.Text.Primary, Offset(x, top), Size(w, h))
            drawRect(
                CgColors.Text.Dim,
                Offset(x + w / 2f - 1.dp.toPx(), top + h / 2f - 9.dp.toPx()),
                Size(2.dp.toPx(), 18.dp.toPx()),
            )
        }
        handle(item.trimInUs, inSide = true)
        handle(item.trimOutUs, inSide = item.trimOutUs == item.trimInUs)
    }
}

/** The sound, one 2 dp bar every 3 dp, with the pump start in green. */
private fun DrawScope.wave(v: TimelineView, pad: Float, waveform: FloatArray?, pumpUs: Long?, durationUs: Long) {
    val top = WAVE.dp.toPx()
    val h = WAVE_H.dp.toPx()
    if (waveform != null && waveform.isNotEmpty()) {
        val bar = 2.dp.toPx()
        var x = pad
        while (x < size.width - pad) {
            val us = v.us(x)
            val peak = waveform[((us.toFloat() / durationUs.coerceAtLeast(1)) * (waveform.size - 1)).toInt()]
            val bh = (peak * h).coerceAtLeast(1f)
            drawRect(CgColors.Text.Dim, Offset(x, top + (h - bh) / 2f), Size(bar, bh))
            x += 3.dp.toPx()
        }
    }
    pumpUs?.let {
        val x = v.x(it)
        if (x in pad..(size.width - pad))
            drawRect(CgColors.Status.Live, Offset(x - 1.dp.toPx(), top), Size(2.dp.toPx(), h))
    }
}

/** The shot's start: an orange line through strip and sound, with its flag at the top. */
private fun DrawScope.flag(item: VideoItem, v: TimelineView) {
    val start = item.shotStartInVideoUs ?: return
    if (item.shot == null) return
    val x = v.x(start)
    val color = if (item.syncLocked) CgColors.Text.Dim else CgColors.Brand.Signal
    drawRect(color, Offset(x - 1.dp.toPx(), 46.dp.toPx()), Size(2.dp.toPx(), 82.dp.toPx()))
    drawRect(color, Offset(x, 44.dp.toPx()), Size(16.dp.toPx(), 12.dp.toPx()))
}

/** The playhead: a line and a triangle on top, the triangle refilled into [tri] (kept by the caller) each frame. */
private fun DrawScope.playheadMark(v: TimelineView, tri: Path) {
    val x = v.x(v.playheadUs)
    drawRect(CgColors.Text.Primary, Offset(x - 1.dp.toPx(), 44.dp.toPx()), Size(2.dp.toPx(), 84.dp.toPx()))
    tri.rewind()
    tri.moveTo(x - 5.dp.toPx(), 44.dp.toPx())
    tri.lineTo(x + 5.dp.toPx(), 44.dp.toPx())
    tri.lineTo(x, 51.dp.toPx())
    tri.close()
    drawPath(tri, CgColors.Text.Primary)
}

/** What a dragged handle catches on. */
private enum class SnapTarget {
    PLAYHEAD,
    SHOT_START,
    SHOT_END,
    PUMP_START,
}

/** The view for a zoom given as a multiple of the fitted scale, capped at [TimelineMath.MAX_PX_PER_SECOND]. */
private fun timelineView(width: Float, durationUs: Long, playheadUs: Long, pad: Float, factor: Float?): TimelineView {
    val fitted = TimelineView(width, durationUs, playheadUs, pad)
    val px = factor?.let { (fitted.fitScale * it).coerceAtMost(TimelineMath.MAX_PX_PER_SECOND) }
    return if (px == null || px <= fitted.fitScale) fitted else fitted.copy(pxPerSecond = px)
}

/** m:ss.t, the web editor's clock. */
fun clock(us: Long): String {
    val tenths = (us / 100_000).coerceAtLeast(0)
    val s = tenths / 10
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}.${tenths % 10}"
}

private fun rulerClock(us: Long): String {
    val s = us / 1_000_000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private fun seconds(us: Long): String = ((us / 100_000) / 10f).toString()

private const val HEIGHT = 128
/** Compact: the strip from the flag's top (44) to its bottom (104), at the top of the canvas. */
private const val COMPACT_SHIFT = 44
private const val COMPACT_HEIGHT = 60
private const val PAD = 16
private const val HEADER_BASELINE = 18
private const val OVERVIEW = 28
private const val RULER_TOP = 38
private const val STRIP = 50
private const val STRIP_H = 54
private const val WAVE = 106
private const val WAVE_H = 16
private const val HANDLE_W = 10
private const val GRAB = 22
private const val SNAP = 8

/** Text layouts kept between frames: more than a wide timeline's labels in one frame. */
private const val TEXT_CACHE = 32

/** A playhead move at least this long (µs) is a jump, and eases there; anything shorter is playback, and follows. */
private const val JUMP_US = 250_000f
/** One sweep of the light across the filmstrip while its frames are made, and the light's width. */
private const val SHIMMER_MS = 1_200
private const val SHIMMER_BAND = 96
