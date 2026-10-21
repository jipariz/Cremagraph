package app.onlyshots.open

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CgIcon
import app.onlyshots.core.designsystem.component.OnlyShotsMark
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.app_name
import app.onlyshots.core.resources.open_banner_detail
import app.onlyshots.core.resources.open_banner_title
import app.onlyshots.core.resources.open_change
import app.onlyshots.core.resources.open_choose
import app.onlyshots.core.resources.open_details_does
import app.onlyshots.core.resources.open_details_does_title
import app.onlyshots.core.resources.open_dismiss
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
import app.onlyshots.core.resources.start_title
import app.onlyshots.core.resources.text
import org.jetbrains.compose.resources.stringResource

/**
 * The web on a phone, from the design's "Mobile web" artboard in the Instrument look: the app's banner on top (it films
 * the shot live, which the page cannot), or, once closed, a header with a small "get the app" key; then the two steps
 * in a hairline frame, what it does and reads behind two folded rows, and "open editor" pinned to the bottom.
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
                .padding(start = CgSpace.S16, end = CgSpace.S16, top = CgSpace.S24, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
                Text(
                    stringResource(if (banner) Res.string.open_mobile_title_banner else Res.string.start_title),
                    style = CgTheme.type.pivot,
                    color = CgColors.Text.Primary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(Res.string.open_mobile_intro),
                    style = CgTheme.type.body.copy(lineHeight = 22.sp),
                    color = CgColors.Text.Secondary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
                val opening = status?.takeIf { it == Opening }?.text()
                StepFrame {
                    StepRow(
                        number = 1,
                        title = stringResource(Res.string.open_phone_video_title),
                        detail =
                            opening
                                ?: picked?.let { videoLine(it, pickedFile) }
                                ?: stringResource(Res.string.open_mobile_video_detail),
                        action = stringResource(if (picked == null) Res.string.open_choose else Res.string.open_change),
                        done = picked != null,
                        next = picked == null,
                        onClick = onPickVideo,
                        modifier = Modifier.testTag(TestTags.OpenPickVideo),
                    )
                    StepDivider()
                    StepRow(
                        number = 2,
                        title = stringResource(Res.string.open_phone_shot_title),
                        detail = shot?.let { shotLine(it) } ?: stringResource(Res.string.open_mobile_shot_detail),
                        action = stringResource(if (shot == null) Res.string.open_choose else Res.string.open_change),
                        done = shot != null,
                        next = picked != null && shot == null,
                        onClick = onPickShot,
                        modifier = Modifier.testTag(TestTags.OpenPickShot),
                    )
                }
                status?.takeIf { it != Opening }?.let { StatusLine(it.text()) }
            }
            Column(Modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Line.Hairline)) {
                Fold(stringResource(Res.string.open_details_does_title), stringResource(Res.string.open_details_does))
                Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
                Fold(
                    stringResource(Res.string.open_mobile_reads_title),
                    stringResource(Res.string.open_mobile_reads) + (videosNote?.let { " $it" } ?: ""),
                )
            }
        }
        // "open editor" stays in reach, whatever is scrolled.
        Column {
            Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
            Column(
                Modifier.fillMaxWidth().padding(start = CgSpace.S16, end = CgSpace.S16, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(ctaHint(picked != null, shot != null)),
                    style = CgTheme.type.bodySmall,
                    color = CgColors.Text.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                OpenEditorKey(ready = picked != null && shot != null, onClick = onOpenEditor, fill = true)
            }
        }
    }
}

/** The app's banner: the mark, what the app adds, "×", and this phone's store key; a cell under a signal rule. */
@Composable
private fun Banner(store: Store, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.padding(start = CgSpace.S12, end = CgSpace.S12, top = CgSpace.S12)
            .fillMaxWidth()
            .background(CgColors.Surface.Bar)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .drawBehind { drawRect(CgColors.Brand.Signal, size = Size(size.width, CgStroke.Rule.toPx())) }
            .padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
            Box(Modifier.size(CgSize.Touch).border(CgStroke.Hairline, CgColors.Line.Hairline), Alignment.Center) {
                OnlyShotsMark(Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(top = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(Res.string.open_banner_title).caps(),
                    style = CgTheme.type.label.copy(letterSpacing = 0.em),
                    color = CgColors.Text.Primary,
                )
                Text(
                    stringResource(Res.string.open_banner_detail),
                    style = CgTheme.type.bodySmall,
                    color = CgColors.Text.Secondary,
                )
            }
            val dismiss = stringResource(Res.string.open_dismiss)
            Box(
                Modifier.size(CgSize.Touch).clickable(role = Role.Button, onClick = onDismiss).semantics {
                    contentDescription = dismiss
                },
                Alignment.Center,
            ) {
                CgIcon(CgIcons.Close, tint = CgColors.Text.Secondary, size = 18.dp)
            }
        }
        StoreButton(store, onOpen, Modifier.fillMaxWidth().padding(end = CgSpace.S8))
    }
}

/** With the banner closed: the name, and the small "get the app" key to the same store. */
@Composable
private fun MobileHeader(store: Store, open: (() -> Unit)?) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .padding(start = CgSpace.S16, end = CgSpace.S12, top = CgSpace.S12, bottom = CgSpace.S12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OnlyShotsMark(Modifier.size(26.dp), splashTarget = true)
                Text(
                    stringResource(Res.string.app_name).caps(),
                    style = CgTheme.type.sheetTitle.copy(letterSpacing = 0.08.em),
                    color = CgColors.Text.Primary,
                )
            }
            if (open != null) {
                val label =
                    stringResource(
                        if (store == Store.Play) Res.string.open_get_app_play else Res.string.open_get_app_app_store
                    )
                Row(
                    Modifier.heightIn(min = CgSize.Touch)
                        .border(CgStroke.Hairline, CgColors.Line.Hairline)
                        .clickable(role = Role.Button, onClick = open)
                        .semantics(mergeDescendants = true) { contentDescription = label }
                        .padding(start = CgSpace.S12, end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(CgSpace.S8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (store == Store.Play) PlayMark(Modifier.size(16.dp, 18.dp))
                    else AppleMark(Modifier.size(15.dp, 18.dp), CgColors.Text.Primary)
                    Text(
                        stringResource(Res.string.open_get_app).caps(),
                        style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em),
                        color = CgColors.Text.Primary,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
    }
}

/** A folded row: its title in Mono capitals and a chevron; open, its text under it. */
@Composable
private fun Fold(title: String, text: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = CgSpace.S16),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.caps(),
                style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em),
                color = CgColors.Text.Primary,
            )
            val turn by animateFloatAsState(if (open) 180f else 0f, CgMotion.settle(), label = "chevron")
            CgIcon(CgIcons.ChevronDown, Modifier.rotate(turn), tint = CgColors.Text.Secondary, size = 18.dp)
        }
        AnimatedVisibility(
            open,
            enter = fadeIn(CgMotion.medium()) + expandVertically(CgMotion.sizeSpec),
            exit = fadeOut(CgMotion.short()) + shrinkVertically(CgMotion.sizeSpec),
        ) {
            Text(
                text,
                style = CgTheme.type.bodySmall.copy(lineHeight = 20.sp),
                color = CgColors.Text.Secondary,
                modifier = Modifier.padding(start = CgSpace.S16, end = CgSpace.S16, bottom = 14.dp),
            )
        }
    }
}
