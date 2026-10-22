package app.onlyshots.style

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.onlyshots.SharedKeys
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CgDialog
import app.onlyshots.core.designsystem.component.CgIcon
import app.onlyshots.core.designsystem.component.CoffeeScene
import app.onlyshots.core.designsystem.component.DialogRisk
import app.onlyshots.core.designsystem.component.SectionHeader
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.model.style.Material
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.style_built_in
import app.onlyshots.core.resources.style_cancel
import app.onlyshots.core.resources.style_copy
import app.onlyshots.core.resources.style_delete
import app.onlyshots.core.resources.style_delete_text
import app.onlyshots.core.resources.style_delete_title
import app.onlyshots.core.resources.style_edit
import app.onlyshots.core.resources.style_edit_a_copy
import app.onlyshots.core.resources.style_long_press_hint
import app.onlyshots.core.resources.style_new_style
import app.onlyshots.core.resources.style_remove
import app.onlyshots.core.resources.style_tile_description
import app.onlyshots.core.resources.style_your_styles
import app.onlyshots.overlay.GraphStyles
import app.onlyshots.shareBounds
import app.onlyshots.testTagsAsResourceIds
import org.jetbrains.compose.resources.stringResource

/** What the picker can ask for, besides picking a style. */
class StylePickerActions(
    val pick: (id: String) -> Unit,
    /** Opens the style editor on a new style starting from [baseId]. */
    val create: (baseId: String) -> Unit,
    /** Opens the style editor on the user's style [id], to change it in place. */
    val edit: (id: String) -> Unit,
    val rename: (id: String, name: String) -> Unit,
    val delete: (id: String) -> Unit,
)

/**
 * The canvas's style sheet: the user's styles first, with "new style" (a copy of the selected style), and under them
 * the selected style's keys (copy · edit · remove; a built-in can only be copied), then the built-in ones. Long-press a
 * style for the same actions. Renaming is the style editor's, in its header.
 */
@Composable
fun StylePicker(currentId: String, custom: List<GraphStyle>, actions: StylePickerActions) {
    var deleting by remember { mutableStateOf<GraphStyle?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        SectionHeader(stringResource(Res.string.style_your_styles))
        // Lazy for its item animations: a style made, renamed or deleted slides the others aside rather than jumping.
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
            items(custom, key = { it.id }) { style ->
                StyleTile(
                    style,
                    picked = style.id == currentId,
                    width = TILE_SMALL,
                    modifier =
                        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = ItemPlacement),
                    onPick = { actions.pick(style.id) },
                    menu = { close ->
                        Item(stringResource(Res.string.style_copy), TestTags.StyleMenuCopy) {
                            close()
                            actions.create(style.id)
                        }
                        Item(stringResource(Res.string.style_edit), TestTags.StyleMenuEdit) {
                            close()
                            actions.edit(style.id)
                        }
                        Item(stringResource(Res.string.style_remove), TestTags.StyleMenuDelete, risky = true) {
                            close()
                            deleting = style
                        }
                    },
                )
            }
            item(key = NEW_STYLE_KEY) {
                NewStyleTile(
                    onClick = { actions.create(currentId) },
                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = ItemPlacement),
                )
            }
        }
        SectionHeader(stringResource(Res.string.style_built_in))
        // As many columns as fit (a sheet on a phone takes three, the wide panel and a wide sheet more); a grid that
        // fills the width rather than three tiles stretched across a tablet.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (maxWidth / TILE_MIN).toInt().coerceAtLeast(3)
            Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
                for (row in GraphStyles.all.chunked(columns)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
                        for (style in row) {
                            Box(Modifier.weight(1f)) {
                                StyleTile(
                                    style,
                                    picked = style.id == currentId,
                                    width = null,
                                    onPick = { actions.pick(style.id) },
                                    builtIn = true,
                                    menu = { close ->
                                        Item(
                                            stringResource(Res.string.style_edit_a_copy),
                                            TestTags.StyleMenuCopy,
                                            onward = true,
                                        ) {
                                            close()
                                            actions.create(style.id)
                                        }
                                    },
                                )
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Text(
            stringResource(Res.string.style_long_press_hint),
            style = CgTheme.type.caption,
            color = CgColors.Text.Secondary,
            modifier =
                Modifier.fillMaxWidth().heightIn(min = CgSize.Touch).wrapContentHeight(Alignment.CenterVertically),
        )
    }
    deleting?.let { style ->
        CgDialog(
            title = stringResource(Res.string.style_delete_title, style.name),
            text = stringResource(Res.string.style_delete_text),
            onDismiss = { deleting = null },
            confirm =
                stringResource(Res.string.style_delete) to
                    {
                        actions.delete(style.id)
                        deleting = null
                    },
            dismiss = stringResource(Res.string.style_cancel) to { deleting = null },
            risk = DialogRisk.Confirm,
        )
    }
}

/**
 * A row of a style's long-press menu, as CgMenuItem draws them (Mono 13 capitals over a divider), with its test tag;
 * [risky] in signal letters, [onward] with an arrow for one that opens the editor.
 */
@Composable
private fun Item(text: String, tag: String, risky: Boolean = false, onward: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text.caps(),
                style = CgTheme.type.labelStrong.copy(fontSize = 13.sp, letterSpacing = 0.02.em),
                color = if (risky) CgColors.Brand.Signal else CgColors.Text.Primary,
            )
        },
        trailingIcon =
            if (onward) {
                { CgIcon(CgIcons.ArrowRight, tint = CgColors.Text.Primary) }
            } else null,
        onClick = onClick,
        modifier =
            Modifier.testTag(tag).drawBehind {
                val h = CgStroke.Hairline.toPx()
                drawRect(CgColors.Line.Divider, Offset(0f, size.height - h), Size(size.width, h))
            },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StyleTile(
    style: GraphStyle,
    picked: Boolean,
    width: androidx.compose.ui.unit.Dp?,
    onPick: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    builtIn: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(Res.string.style_tile_description, style.name)
    val edge by animateColorAsState(if (picked) CgColors.Brand.Bone else CgColors.Line.Hairline, CgMotion.short())
    val edgeWidth by animateDpAsState(if (picked) CgStroke.Rule else CgStroke.Hairline, CgMotion.short())
    Column(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .border(edgeWidth, edge)
            .padding(edgeWidth)
            .testTag(TestTags.styleTile(style.id))
            .semantics {
                selected = picked
                contentDescription = description
            }
            .combinedClickable(role = Role.RadioButton, onLongClick = { open = true }, onClick = onPick)
    ) {
        Box {
            // The style editor's preview grows out of this tile when the style is edited (or copied), and shrinks back.
            StyleSwatch(
                style,
                picked,
                Modifier.fillMaxWidth().height(TILE_HEIGHT).shareBounds(SharedKeys.style(style.id), clip = TileShape),
            )
            // A hairline-framed menu; a built-in's, which only copies, wears the signal rule on top like a sheet.
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier =
                    Modifier.testTagsAsResourceIds()
                        .then(
                            if (builtIn)
                                Modifier.drawBehind {
                                    drawRect(CgColors.Line.Hairline, size = Size(size.width, CgStroke.Rule.toPx()))
                                }
                            else Modifier
                        ),
                shape = CgShape.Chip,
                containerColor = CgColors.Surface.Bar,
                border = BorderStroke(CgStroke.Hairline, CgColors.Line.Hairline),
            ) {
                menu { open = false }
            }
        }
        Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
        Text(
            style.name.caps(),
            style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em),
            color = CgColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = CgSpace.S8, vertical = CgSpace.S6),
        )
    }
}

@Composable
private fun NewStyleTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.style_new_style)
    Column(
        modifier
            .width(TILE_SMALL)
            .heightIn(min = TILE_HEIGHT + NAME_STRIP)
            .testTag(TestTags.StyleNew)
            .drawDashedBorder()
            .combinedClickableCompat(onClick)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(CgSpace.S4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CgIcon(CgIcons.Plus, tint = CgColors.Text.Primary, size = 24.dp)
        Text(label.caps(), style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    combinedClickable(role = Role.Button, onClick = onClick)

private fun Modifier.drawDashedBorder(): Modifier = drawBehind {
    val w = CgStroke.Hairline.toPx()
    drawRect(
        CgColors.Line.Hairline,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/**
 * A small picture of [style]: its panel (colour, gradient or tint over the scene) on the drawn espresso scene, with a
 * pressure and a flow curve in its colours. Not a real overlay — at this size the text would be noise — but the same
 * surface and colours.
 */
@Composable
fun StyleSwatch(style: GraphStyle, picked: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds()) {
        CoffeeScene(Modifier.fillMaxSize(), progress = 0.6f)
        Canvas(Modifier.fillMaxSize()) {
            val inset = 6.dp.toPx()
            val top = size.height * 0.2f
            val panel = Size(size.width - 2 * inset, size.height - top - inset)
            val at = Offset(inset, top)
            val radius = CornerRadius((style.panel.radius / 2f).dp.toPx())
            val p = style.panel
            val tiles = p.tiles
            when {
                p.material == Material.NONE -> {}
                p.gradient != null ->
                    drawRoundRect(
                        Brush.verticalGradient(
                            *p.gradient!!.map { it.at to Color(it.color) }.toTypedArray(),
                            startY = at.y,
                            endY = at.y + panel.height,
                        ),
                        at,
                        panel,
                        radius,
                    )
                else -> {
                    if (tiles != null) drawRoundRect(Color(tiles.base), at, panel, radius)
                    drawRoundRect(Color(p.color).copy(alpha = p.opacity.coerceIn(0.15f, 1f)), at, panel, radius)
                }
            }
            if (p.material == Material.GLASS && p.glass.edge > 0f) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.5f),
                    at,
                    panel,
                    radius,
                    style = Stroke(1.dp.toPx()),
                )
            }
            fun curve(points: List<Pair<Float, Float>>, color: Color) {
                val path = Path()
                points.forEachIndexed { i, (x, y) ->
                    val px = at.x + panel.width * (0.08f + 0.84f * x)
                    val py = at.y + panel.height * (0.15f + 0.7f * y)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color, style = Stroke(2.dp.toPx()))
            }
            curve(listOf(0f to 0.95f, 0.3f to 0.2f, 0.45f to 0.6f, 1f to 0.3f), Color(style.series.pressure))
            curve(listOf(0f to 1f, 0.35f to 0.75f, 0.7f to 0.65f, 1f to 0.55f), Color(style.series.flow))
        }
    }
}

private val TILE_HEIGHT = 64.dp
private val TILE_SMALL = 104.dp

/** A built-in style's tile is never narrower than this. */
private val TILE_MIN = 110.dp

private val TileShape = RectangleShape

/** The name under a tile's picture. */
private val NAME_STRIP = 30.dp

private const val NEW_STYLE_KEY = "new-style"

/** A tile moving to its new place in the row: it slides there and stops; tiles never fade in or out. */
private val ItemPlacement = tween<IntOffset>(CgMotion.MEDIUM, easing = CgMotion.Mechanical)
