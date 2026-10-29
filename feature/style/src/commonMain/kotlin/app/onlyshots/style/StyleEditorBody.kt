package app.onlyshots.style

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.EditorSheetSlot
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.layout.LayoutKind
import app.onlyshots.layout.LocalWindowLayout
import org.jetbrains.compose.resources.stringResource

/**
 * The editor without a view model, so screenshot tests can draw every tab. Resolves its parts once ([StyleEditorSlots])
 * and hands them to the layout the window calls for: [StyleEditorWideLayout] on a wide window, else
 * [StyleEditorPhoneLayout].
 */
@Composable
fun StyleEditorBody(
    draft: GraphStyle,
    frames: List<PreviewFrame>,
    onChange: ((GraphStyle) -> GraphStyle) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    live: LivePicture? = null,
    /** A style not saved yet: the editor opens on its name. */
    isNew: Boolean = false,
    initialTab: StyleTab = StyleTab.PANEL,
    sheetOverride: (@Composable () -> Unit)? = null,
    /** The style tile this editor was opened from, which its preview grows out of. */
    sharedKey: Any? = null,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var frame by remember { mutableIntStateOf(0) }
    var color by remember { mutableStateOf<ColorTarget?>(null) }
    val still = frames.getOrNull(stepFrame(frame, 0, frames.size))
    val luma = live?.luma ?: still?.luma ?: SCENE_LUMA
    // The save sheet, else a colour's: one slides up at a time, and back down; the last one stays while it leaves.
    var lastOverride by remember { mutableStateOf(sheetOverride) }
    if (sheetOverride != null) lastOverride = sheetOverride
    val sheet = styleSheet(saving = sheetOverride != null, color)
    val slots =
        StyleEditorSlots(
            header = { m ->
                StyleEditorHeader(
                    draft.name,
                    onRename = { name -> onChange { it.copy(name = name) } },
                    onBack = onBack,
                    onSave = onSave,
                    modifier = m,
                    nameFirst = isNew,
                )
            },
            preview = { m, full ->
                StylePreview(
                    draft,
                    still?.image,
                    live,
                    luma,
                    frames.size,
                    onFrame = { d -> frame = stepFrame(frame, d, frames.size) },
                    modifier = m,
                    full = full,
                    sharedKey = sharedKey,
                )
            },
            tabs = { tall -> StyleTabs(tab, onTab = { tab = it }, tall = tall) },
            controls = { m ->
                StyleTabControls(tab, draft, luma, onChange, onPickColor = { color = it }, modifier = m)
            },
            sheets = {
                EditorSheetSlot(sheet) { shown ->
                    when (shown) {
                        StyleSheet.Save -> lastOverride?.invoke()
                        is StyleSheet.Color -> ColorSheet(shown.target, draft, onChange, onDone = { color = null })
                    }
                }
            },
        )
    val layout = LocalWindowLayout.current
    when (layout.kind) {
        LayoutKind.Wide ->
            StyleEditorWideLayout(
                layout,
                slots,
                sheetOpen = sheet != null,
                onScrimTap = { color = null },
                modifier = modifier,
            )
        LayoutKind.Phone,
        LayoutKind.Stacked,
        LayoutKind.Tiny -> StyleEditorPhoneLayout(slots, modifier)
    }
}

/** The editor's parts, resolved once by [StyleEditorBody], each placed by the layout. */
internal class StyleEditorSlots(
    /** Back, the style's name, save. */
    val header: @Composable (Modifier) -> Unit,
    /**
     * The picture with the graph on it: [full] the whole frame fitted to the space, else the strip the graph sits on.
     */
    val preview: @Composable (Modifier, full: Boolean) -> Unit,
    /** The tabs; [tall] as tall as the header beside them. */
    val tabs: @Composable (tall: Boolean) -> Unit,
    /** The tab's controls, scrolling. */
    val controls: @Composable (Modifier) -> Unit,
    /** The save or colour sheet, sliding up from the bottom of the space it is placed in. */
    val sheets: @Composable () -> Unit,
)

/** The [tab]'s controls, scrolling. */
@Composable
private fun StyleTabControls(
    tab: StyleTab,
    draft: GraphStyle,
    luma: Float,
    onChange: ((GraphStyle) -> GraphStyle) -> Unit,
    onPickColor: (ColorTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(CgSpace.S16),
        verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        // A tab's controls slide across from the side the tab sits on, the last ones out the other way, clipped to
        // the panel; rows that come and go inside move the height.
        AnimatedContent(
            tab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                slideInHorizontally(CgMotion.offsetSpec) { dir * it }
                    .togetherWith(slideOutHorizontally(CgMotion.offsetSpec) { -dir * it })
                    .using(SizeTransform(clip = true) { _, _ -> CgMotion.sizeSpec })
            },
            label = "tab",
        ) { shown ->
            Column(
                Modifier.animateContentSize(CgMotion.sizeSpec),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
            ) {
                when (shown) {
                    StyleTab.PANEL -> PanelControls(draft, onChange, onPickColor)
                    StyleTab.LINES -> LineControls(draft, onChange, onPickColor)
                    StyleTab.GRID -> GridControls(draft, onChange, onPickColor)
                    StyleTab.TEXT -> TextControls(draft, onChange, onPickColor)
                    StyleTab.READINGS -> ReadingControls(draft, luma, onChange, onPickColor)
                }
            }
        }
    }
}

/** The tabs in a row, the picked one underlined. */
@Composable
private fun StyleTabs(
    tab: StyleTab,
    onTab: (StyleTab) -> Unit,
    modifier: Modifier = Modifier,
    /** As tall as the header beside it (a wide window), so the two rules line up. */
    tall: Boolean = false,
) {
    // The underline slides from tab to tab; drawn under the row, so only drawing follows it.
    val at = remember { Animatable(tab.ordinal.toFloat()) }
    LaunchedEffect(tab) { at.animateTo(tab.ordinal.toFloat(), CgMotion.settle()) }
    val count = StyleTab.entries.size
    Row(
        modifier.fillMaxWidth().height(if (tall) HEADER_HEIGHT else CgSize.Touch).drawBehind {
            drawBottomHairline()
            val w = size.width / count
            val h = CgStroke.Rule.toPx()
            drawRect(CgColors.Brand.Bone, Offset(w * at.value, size.height - h), Size(w, h))
        }
    ) {
        for (t in StyleTab.entries) {
            val on = t == tab
            val ink by animateColorAsState(if (on) CgColors.Text.Primary else CgColors.Text.Secondary, CgMotion.short())
            Box(
                Modifier.weight(1f).fillMaxSize().testTag(TestTags.styleTab(t.name.lowercase())).clickable(
                    role = Role.Tab
                ) {
                    onTab(t)
                },
                Alignment.Center,
            ) {
                Text(stringResource(t.label).caps(), style = CgTheme.type.labelStrong, color = ink, maxLines = 1)
            }
        }
    }
}

/** The hairline along the bottom edge, where the header, the strip and the tabs meet what is under them. */
internal fun DrawScope.drawBottomHairline() {
    val h = CgStroke.Hairline.toPx()
    drawRect(CgColors.Line.Hairline, Offset(0f, size.height - h), Size(size.width, h))
}

/** The hairline along the start edge, where the wide window's panel meets the frame. */
internal fun DrawScope.drawStartHairline() {
    drawRect(CgColors.Line.Hairline, Offset.Zero, Size(CgStroke.Hairline.toPx(), size.height))
}
