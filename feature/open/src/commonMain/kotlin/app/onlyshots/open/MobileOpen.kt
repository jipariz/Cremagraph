package app.onlyshots.open

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.OnlyShotsMark
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.app_name
import app.onlyshots.core.resources.open_banner_detail
import app.onlyshots.core.resources.open_banner_title
import app.onlyshots.core.resources.open_choose
import app.onlyshots.core.resources.open_details_does
import app.onlyshots.core.resources.open_details_does_title
import app.onlyshots.core.resources.open_dismiss
import app.onlyshots.core.resources.open_editor
import app.onlyshots.core.resources.open_get_app
import app.onlyshots.core.resources.open_get_app_app_store
import app.onlyshots.core.resources.open_get_app_play
import app.onlyshots.core.resources.open_mobile_intro
import app.onlyshots.core.resources.open_mobile_reads
import app.onlyshots.core.resources.open_mobile_reads_title
import app.onlyshots.core.resources.open_mobile_shot_detail
import app.onlyshots.core.resources.open_mobile_title_banner
import app.onlyshots.core.resources.open_mobile_video_detail
import app.onlyshots.core.resources.open_phone_shot_title
import app.onlyshots.core.resources.open_phone_video_title
import app.onlyshots.core.resources.open_video
import app.onlyshots.core.resources.start_title
import app.onlyshots.core.resources.text
import org.jetbrains.compose.resources.stringResource

/**
 * The web on a phone, from the design's "Mobile web" artboard: the app's banner on top (it films the shot live, which
 * the page cannot), or, once closed, a header with a small "get the app" link; then the two steps, what it does and
 * reads behind two folded rows, and "open editor" pinned to the bottom.
 */
@Composable
internal fun MobileOpenBody(
    picked: VideoItem?,
    pickedFile: PickedFile?,
    shot: ChosenShot?,
    status: UiText?,
    onPickVideo: () -> Unit,
    onPickShot: () -> Unit,
    onOpenEditor: () -> Unit,
    stores: StoreLinks,
    videosNote: String?,
    modifier: Modifier = Modifier,
) {
    val store = if (stores.onAppStore != null && stores.onPlay == null) Store.AppStore else Store.Play
    val open = if (store == Store.Play) stores.onPlay else stores.onAppStore
    val banner = open != null && !stores.bannerDismissed
    Column(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding()) {
        if (banner && open != null) {
            Banner(store, onOpen = open, onDismiss = stores.onDismissBanner)
        } else {
            MobileHeader(store, open)
        }
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (banner) Res.string.open_mobile_title_banner else Res.string.start_title),
                    style =
                        CgTheme.type.body.copy(
                            fontSize = if (banner) 28.sp else 30.sp,
                            lineHeight = if (banner) 31.sp else 34.sp,
                            fontWeight = FontWeight.Light,
                        ),
                    color = CgColors.Text.Primary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(Res.string.open_mobile_intro),
                    style = CgTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = CgColors.Text.Secondary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val opening = status?.takeIf { it == Opening }?.text()
                StepSwap(picked != null) { done ->
                    if (!done || picked == null) {
                        CompactStep(
                            1,
                            stringResource(Res.string.open_phone_video_title),
                            opening ?: stringResource(Res.string.open_mobile_video_detail),
                            next = true,
                            onClick = onPickVideo,
                            tag = TestTags.OpenPickVideo,
                        )
                    } else {
                        PickedStep(
                            name = pickedFile?.name ?: stringResource(Res.string.open_video),
                            detail = videoLine(picked, pickedFile),
                            tile = { VideoTile(pickedFile?.thumb, picked.id) },
                            onChange = onPickVideo,
                            highlighted = false,
                            tag = TestTags.OpenPickVideo,
                        )
                    }
                }
                StepSwap(shot != null) { done ->
                    if (!done || shot == null) {
                        CompactStep(
                            2,
                            stringResource(Res.string.open_phone_shot_title),
                            stringResource(Res.string.open_mobile_shot_detail),
                            next = picked != null,
                            onClick = onPickShot,
                            tag = TestTags.OpenPickShot,
                        )
                    } else {
                        PickedStep(
                            name = shot.label,
                            detail = shotLine(shot),
                            tile = { ShotTile() },
                            onChange = onPickShot,
                            highlighted = false,
                            tag = TestTags.OpenPickShot,
                        )
                    }
                }
                status
                    ?.takeIf { it != Opening }
                    ?.let { Text(it.text(), style = CgTheme.type.body, color = CgColors.Status.Highlight) }
            }
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(CgColors.Line.Divider))
                Fold(stringResource(Res.string.open_details_does_title), stringResource(Res.string.open_details_does))
                Box(Modifier.fillMaxWidth().height(1.dp).background(CgColors.Line.Divider))
                Fold(
                    stringResource(Res.string.open_mobile_reads_title),
                    stringResource(Res.string.open_mobile_reads) + (videosNote?.let { " $it" } ?: ""),
                )
            }
        }
        // "open editor" stays in reach, whatever is scrolled.
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(CgColors.Line.Divider))
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val ready = picked != null && shot != null
                Text(
                    stringResource(ctaHint(picked != null, shot != null)),
                    style = CgTheme.type.body.copy(fontSize = 13.sp),
                    color = CgColors.Text.Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    Modifier.testTag(TestTags.OpenEditor)
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            animateColorAsState(if (ready) CgColors.Brand.Signal else CgColors.Surface.Raised).value
                        )
                        .clickable(enabled = ready, role = Role.Button, onClick = onOpenEditor),
                    Alignment.Center,
                ) {
                    Text(
                        stringResource(Res.string.open_editor),
                        style = CgTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        color = animateColorAsState(if (ready) CgColors.Text.OnLight else CgColors.Text.Dim).value,
                    )
                }
            }
        }
    }
}

/** The app's banner: the mark, what the app adds, "×", and this phone's store button. */
@Composable
private fun Banner(store: Store, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(CgColors.Surface.Bar)
            .border(1.dp, CgColors.Line.Hairline, shape)
            .padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(CgColors.Surface.Raised),
                Alignment.Center,
            ) {
                OnlyShotsMark(Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(top = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(Res.string.open_banner_title),
                    style = CgTheme.type.body.copy(fontSize = 16.sp),
                    color = CgColors.Text.Primary,
                )
                Text(
                    stringResource(Res.string.open_banner_detail),
                    style = CgTheme.type.body.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = CgColors.Text.Secondary,
                )
            }
            val dismiss = stringResource(Res.string.open_dismiss)
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss).semantics {
                    contentDescription = dismiss
                },
                Alignment.Center,
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val s = size.width / 16f
                    drawLine(CgColors.Text.Dim, Offset(4 * s, 4 * s), Offset(12 * s, 12 * s), 1.6f * s, StrokeCap.Round)
                    drawLine(CgColors.Text.Dim, Offset(12 * s, 4 * s), Offset(4 * s, 12 * s), 1.6f * s, StrokeCap.Round)
                }
            }
        }
        StoreButton(store, onOpen, Modifier.fillMaxWidth().padding(end = 8.dp))
    }
}

/** With the banner closed: the name, and the small "get the app" link to the same store. */
@Composable
private fun MobileHeader(store: Store, open: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OnlyShotsMark(Modifier.size(26.dp), splashTarget = true)
            Text(
                stringResource(Res.string.app_name),
                style = CgTheme.type.body.copy(fontSize = 19.sp),
                color = CgColors.Brand.Bone,
            )
        }
        if (open != null) {
            val shape = RoundedCornerShape(22.dp)
            val label =
                stringResource(
                    if (store == Store.Play) Res.string.open_get_app_play else Res.string.open_get_app_app_store
                )
            Row(
                Modifier.heightIn(min = 44.dp)
                    .clip(shape)
                    .border(1.dp, CgColors.Line.Hairline, shape)
                    .clickable(role = Role.Button, onClick = open)
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .padding(start = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (store == Store.Play) PlayMark(Modifier.size(16.dp, 18.dp))
                else AppleMark(Modifier.size(15.dp, 18.dp))
                Text(
                    stringResource(Res.string.open_get_app),
                    style = CgTheme.type.body.copy(fontSize = 15.sp),
                    color = CgColors.Brand.Bone,
                )
            }
        }
    }
}

/** A step to do, compact: its number, what goes in, and "choose" (light when it is the next thing to do). */
@Composable
private fun CompactStep(
    number: Int,
    title: String,
    detail: String,
    next: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val radius = 18.dp
    val shape = RoundedCornerShape(radius)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(CgColors.Surface.Bar)
            .then(
                if (next) Modifier.drawBehind { dashedBorder(CgColors.Brand.Signal, radius.toPx()) }
                else Modifier.border(1.dp, CgColors.Line.Hairline, shape)
            )
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(CgColors.Surface.Raised), Alignment.Center) {
            Text("$number", style = CgTheme.type.body.copy(fontSize = 16.sp), color = CgColors.Text.Primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = CgTheme.type.body.copy(fontSize = 17.sp), color = CgColors.Text.Primary)
            Text(detail, style = CgTheme.type.body.copy(fontSize = 13.sp), color = CgColors.Text.Dim)
        }
        PillButton(
            stringResource(Res.string.open_choose),
            light = next,
            outlined = !next,
            onClick = onClick,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** A folded row: its title and a chevron; open, its text under it. */
@Composable
private fun Fold(title: String, text: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = CgTheme.type.body.copy(fontSize = 15.sp), color = CgColors.Brand.Bone)
            val turn by animateFloatAsState(if (open) 180f else 0f, CgMotion.settle(), label = "chevron")
            Canvas(Modifier.size(16.dp).rotate(turn)) {
                val s = size.width / 16f
                drawPath(
                    Path().apply {
                        moveTo(4 * s, 6 * s)
                        lineTo(8 * s, 10 * s)
                        lineTo(12 * s, 6 * s)
                    },
                    CgColors.Text.Dim,
                    style = Stroke(1.6f * s, cap = StrokeCap.Round),
                )
            }
        }
        AnimatedVisibility(
            open,
            enter = fadeIn(CgMotion.medium()) + expandVertically(CgMotion.sizeSpec),
            exit = fadeOut(CgMotion.short()) + shrinkVertically(CgMotion.sizeSpec),
        ) {
            Text(
                text,
                style = CgTheme.type.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = CgColors.Text.Secondary,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
            )
        }
    }
}
