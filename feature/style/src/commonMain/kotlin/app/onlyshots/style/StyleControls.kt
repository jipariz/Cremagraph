package app.onlyshots.style

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.onlyshots.TestTags
import app.onlyshots.connect.label
import app.onlyshots.connect.labelRes
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CgIcon
import app.onlyshots.core.designsystem.component.CgToggle
import app.onlyshots.core.designsystem.component.ColorChip
import app.onlyshots.core.designsystem.component.SegmentedControl
import app.onlyshots.core.model.style.CellLook
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.model.style.GridMode
import app.onlyshots.core.model.style.Lettering
import app.onlyshots.core.model.style.Material
import app.onlyshots.core.model.style.OverlayFont
import app.onlyshots.core.model.style.Reading
import app.onlyshots.core.model.style.Shadow
import app.onlyshots.core.model.style.TargetLook
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.style_adapt_to_video
import app.onlyshots.core.resources.style_area_fill
import app.onlyshots.core.resources.style_blur
import app.onlyshots.core.resources.style_border
import app.onlyshots.core.resources.style_cell_border
import app.onlyshots.core.resources.style_cell_colour
import app.onlyshots.core.resources.style_cell_filled
import app.onlyshots.core.resources.style_cell_glossy
import app.onlyshots.core.resources.style_cell_outlined
import app.onlyshots.core.resources.style_cell_plain
import app.onlyshots.core.resources.style_cell_tiles
import app.onlyshots.core.resources.style_cells
import app.onlyshots.core.resources.style_colour
import app.onlyshots.core.resources.style_corners
import app.onlyshots.core.resources.style_custom
import app.onlyshots.core.resources.style_dashed
import app.onlyshots.core.resources.style_dotted
import app.onlyshots.core.resources.style_drop
import app.onlyshots.core.resources.style_faint
import app.onlyshots.core.resources.style_font
import app.onlyshots.core.resources.style_font_mono
import app.onlyshots.core.resources.style_font_sans
import app.onlyshots.core.resources.style_gloss
import app.onlyshots.core.resources.style_gloss_above
import app.onlyshots.core.resources.style_gloss_below
import app.onlyshots.core.resources.style_gloss_bottom
import app.onlyshots.core.resources.style_gloss_part
import app.onlyshots.core.resources.style_gloss_top
import app.onlyshots.core.resources.style_glow
import app.onlyshots.core.resources.style_glow_colour
import app.onlyshots.core.resources.style_glow_size
import app.onlyshots.core.resources.style_grid
import app.onlyshots.core.resources.style_grid_colour
import app.onlyshots.core.resources.style_halo
import app.onlyshots.core.resources.style_inner_border
import app.onlyshots.core.resources.style_label_colour
import app.onlyshots.core.resources.style_labels
import app.onlyshots.core.resources.style_lettering
import app.onlyshots.core.resources.style_lettering_readout
import app.onlyshots.core.resources.style_lettering_words
import app.onlyshots.core.resources.style_light
import app.onlyshots.core.resources.style_line_shadow
import app.onlyshots.core.resources.style_line_weight
import app.onlyshots.core.resources.style_material
import app.onlyshots.core.resources.style_more_for_panel
import app.onlyshots.core.resources.style_none
import app.onlyshots.core.resources.style_note_blur
import app.onlyshots.core.resources.style_note_glass
import app.onlyshots.core.resources.style_note_no_panel
import app.onlyshots.core.resources.style_note_tiles
import app.onlyshots.core.resources.style_off
import app.onlyshots.core.resources.style_on
import app.onlyshots.core.resources.style_opacity
import app.onlyshots.core.resources.style_other_curves
import app.onlyshots.core.resources.style_outer_border
import app.onlyshots.core.resources.style_over_bright_video
import app.onlyshots.core.resources.style_padding
import app.onlyshots.core.resources.style_panel_colour
import app.onlyshots.core.resources.style_panel_shadow
import app.onlyshots.core.resources.style_phase_line_colour
import app.onlyshots.core.resources.style_phase_lines
import app.onlyshots.core.resources.style_phase_name_colour
import app.onlyshots.core.resources.style_phase_names
import app.onlyshots.core.resources.style_regular
import app.onlyshots.core.resources.style_rows
import app.onlyshots.core.resources.style_rows_and_columns
import app.onlyshots.core.resources.style_scrim
import app.onlyshots.core.resources.style_semibold
import app.onlyshots.core.resources.style_series_colours
import app.onlyshots.core.resources.style_shadow
import app.onlyshots.core.resources.style_shape
import app.onlyshots.core.resources.style_smooth
import app.onlyshots.core.resources.style_softness
import app.onlyshots.core.resources.style_solid
import app.onlyshots.core.resources.style_step_at
import app.onlyshots.core.resources.style_stepped
import app.onlyshots.core.resources.style_target_opacity
import app.onlyshots.core.resources.style_target_value_colour
import app.onlyshots.core.resources.style_target_values
import app.onlyshots.core.resources.style_target_weight
import app.onlyshots.core.resources.style_targets
import app.onlyshots.core.resources.style_text
import app.onlyshots.core.resources.style_text_halo_over_bright_video
import app.onlyshots.core.resources.style_text_over_bright_video
import app.onlyshots.core.resources.style_text_shadow
import app.onlyshots.core.resources.style_tile_base
import app.onlyshots.core.resources.style_tile_edge
import app.onlyshots.core.resources.style_tile_gap
import app.onlyshots.core.resources.style_tiles
import app.onlyshots.core.resources.style_tint
import app.onlyshots.core.resources.style_tint_strength
import app.onlyshots.core.resources.style_title
import app.onlyshots.core.resources.style_title_colour
import app.onlyshots.core.resources.style_under_the_text
import app.onlyshots.core.resources.style_value_weight
import app.onlyshots.core.resources.style_values
import app.onlyshots.core.resources.style_white
import app.onlyshots.core.resources.style_x_colour
import app.onlyshots.core.resources.style_x_over_bright_video
import app.onlyshots.core.resources.style_x_tile
import app.onlyshots.core.resources.style_x_value
import app.onlyshots.core.resources.text
import app.onlyshots.overlay.compose.label
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The style editor's tabs. Every change goes through StyleEdits, the functions BuiltInsAreReplicableTest rebuilds the
 * built-in styles with; what is rarely needed sits under each tab's "more".
 */

private typealias Change = ((GraphStyle) -> GraphStyle) -> Unit

private typealias Pick = (ColorTarget) -> Unit

// Panel ---------------------------------------------------------------------------------------

@Composable
internal fun PanelControls(s: GraphStyle, change: Change, pickColor: Pick) {
    val materials = listOf(Material.SOLID, Material.BLUR, Material.GLASS, Material.NONE)
    ChoiceRow(stringResource(Res.string.style_material)) {
        SegmentedControl(
            listOf(
                stringResource(Res.string.style_solid),
                stringResource(Res.string.style_blur),
                "Liquid Glass",
                stringResource(Res.string.style_none),
            ),
            materials.indexOf(s.panel.material),
            fill = true,
            onSelect = { i -> change { it.withMaterial(materials[i]) } },
        )
    }
    val p = s.panel
    if (p.material == Material.NONE) {
        Note(stringResource(Res.string.style_note_no_panel))
        return
    }
    val solid = p.material == Material.SOLID
    ColorRow(
        stringResource(if (solid) Res.string.style_colour else Res.string.style_tint),
        ColorTarget(
            if (solid) Res.string.style_panel_colour.text() else Res.string.style_tint.text(),
            { it.panel.color },
            { st, c -> st.withPanelColor(c) },
        ),
        s,
        pickColor,
    )
    SliderRow(
        stringResource(if (solid) Res.string.style_opacity else Res.string.style_tint_strength),
        p.opacity,
        if (solid) 0.2f..1f else 0f..1f,
        percent(p.opacity),
    ) { v ->
        change { it.withPanelOpacity(step(v, 0.01f)) }
    }
    when (p.material) {
        Material.BLUR -> {
            SliderRow(stringResource(Res.string.style_blur), p.blur, 8f..48f, "${p.blur.roundToInt()}") { v ->
                change { it.withPanelBlur(v.roundToInt().toFloat()) }
            }
            Corners(s, change)
            StyleRow(stringResource(Res.string.style_tiles)) {
                CgToggle(
                    p.tiles != null,
                    onCheckedChange = { on -> change { it.withTiles(if (on) DefaultTiles else null) } },
                )
            }
            p.tiles?.let { t ->
                Note(stringResource(Res.string.style_note_tiles))
                SliderRow(stringResource(Res.string.style_tile_gap), t.gap, 0f..12f, px(t.gap)) { v ->
                    change { st -> st.withTiles(st.panel.tiles?.copy(gap = v.roundToInt().toFloat())) }
                }
                ColorRow(
                    stringResource(Res.string.style_tile_base),
                    ColorTarget(
                        Res.string.style_tile_base.text(),
                        { it.panel.tiles?.base ?: 0 },
                        { st, c -> st.withTiles(st.panel.tiles?.copy(base = c)) },
                        withOpacity = true,
                    ),
                    s,
                    pickColor,
                )
                ColorRow(
                    stringResource(Res.string.style_tile_edge),
                    ColorTarget(
                        Res.string.style_tile_edge.text(),
                        { it.panel.tiles?.edgeLine ?: 0 },
                        { st, c -> st.withTiles(st.panel.tiles?.copy(edgeLine = c)) },
                        withOpacity = true,
                    ),
                    s,
                    pickColor,
                )
            }
            Note(stringResource(Res.string.style_note_blur))
        }
        Material.GLASS -> {
            GlassControls(p.glass) { g -> change { it.withGlass(g(it.panel.glass)) } }
            Note(stringResource(Res.string.style_note_glass))
        }
        else -> Corners(s, change)
    }
    SliderRow(stringResource(Res.string.style_padding), p.padding, 0f..14f, px(p.padding)) { v ->
        change { it.withPadding(v.roundToInt().toFloat()) }
    }
    StyleRow(stringResource(Res.string.style_adapt_to_video)) {
        CgToggle(s.adapt.enabled, onCheckedChange = { on -> change { it.withAdapt(on) } })
    }
    More(stringResource(Res.string.style_more_for_panel)) {
        EffectControls(
            stringResource(Res.string.style_shadow),
            p.shadow,
            PanelShadow,
            halo = false,
            ColorTarget(
                Res.string.style_panel_shadow.text(),
                { it.panel.shadow?.color ?: 0 },
                { st, c -> st.withPanelShadow(st.panel.shadow?.copy(color = c)) },
                withOpacity = true,
            ),
            s,
            pickColor,
            maxBlur = 24f,
            maxOffset = 8f,
        ) { e ->
            change { it.withPanelShadow(e) }
        }
        ToggleColorRow(
            stringResource(Res.string.style_outer_border),
            p.outerBorder,
            0xD90D1F38.toInt(),
            ColorTarget(
                Res.string.style_outer_border.text(),
                { it.panel.outerBorder },
                { st, c -> st.withOuterBorder(c) },
                withOpacity = true,
            ),
            s,
            change,
            pickColor,
        )
        ToggleColorRow(
            stringResource(Res.string.style_inner_border),
            p.innerBorder,
            0xB3FFFFFF.toInt(),
            ColorTarget(
                Res.string.style_inner_border.text(),
                { it.panel.innerBorder },
                { st, c -> st.withInnerBorder(c) },
                withOpacity = true,
            ),
            s,
            change,
            pickColor,
        )
        StyleRow(stringResource(Res.string.style_glow)) {
            CgToggle(p.glow != null, onCheckedChange = { on -> change { it.withGlow(if (on) DefaultGlow else null) } })
        }
        p.glow?.let { g ->
            ColorRow(
                stringResource(Res.string.style_glow_colour),
                ColorTarget(
                    Res.string.style_glow_colour.text(),
                    { it.panel.glow?.color ?: 0 },
                    { st, c -> st.withGlow(st.panel.glow?.copy(color = c)) },
                    withOpacity = true,
                ),
                s,
                pickColor,
            )
            SliderRow(stringResource(Res.string.style_glow_size), g.radius, 0f..30f, px(g.radius)) { v ->
                change { st -> st.withGlow(st.panel.glow?.copy(radius = v.roundToInt().toFloat())) }
            }
        }
        GlossControls(
            Res.string.style_gloss,
            Gloss.of(p.gradient),
            fallback = { Gloss.around(s.panel.color) },
            s,
            change,
            pickColor,
            get = { Gloss.of(it.panel.gradient) },
            set = { st, g -> st.withPanelGloss(g) },
        )
    }
}

@Composable
private fun Corners(s: GraphStyle, change: Change) {
    SliderRow(stringResource(Res.string.style_corners), s.panel.radius, 0f..28f, px(s.panel.radius)) { v ->
        change { it.withCorners(v.roundToInt().toFloat()) }
    }
}

// Lines ---------------------------------------------------------------------------------------

@Composable
internal fun LineControls(s: GraphStyle, change: Change, pickColor: Pick) {
    // The curves' colours in one framed list: each row its line, its name and its colour's name; tap for the sheet.
    ColorList {
        for (series in StyleSeries) {
            val c = s.series.of(series)
            ColorListRow(
                series.label,
                colorName(c).text(),
                swatch = { Box(Modifier.size(28.dp, 2.dp).background(Color(c))) },
                height = SERIES_ROW,
            ) {
                pickColor(
                    ColorTarget(
                        Res.string.style_x_colour.text(series.labelRes.text()),
                        { it.series.of(series) },
                        { st, v -> st.withSeriesColor(series, v) },
                    )
                )
            }
        }
    }
    val l = s.lines
    SliderRow(stringResource(Res.string.style_line_weight), l.main, 1.5f..4f, px1(l.main)) { v ->
        change { it.withLineWeights(step(v, 0.1f), it.lines.secondary) }
    }
    SliderRow(stringResource(Res.string.style_other_curves), l.secondary, 1f..4f, px1(l.secondary)) { v ->
        change { it.withLineWeights(it.lines.main, step(v, 0.1f)) }
    }
    SliderRow(
        stringResource(Res.string.style_area_fill),
        l.area,
        0f..0.4f,
        if (l.area <= 0f) stringResource(Res.string.style_off) else percent(l.area),
    ) { v ->
        change { it.withArea(step(v, 0.02f)) }
    }
    SliderRow(stringResource(Res.string.style_target_opacity), l.targetOpacity, 0.1f..1f, percent(l.targetOpacity)) { v
        ->
        change { it.withTargetOpacity(step(v, 0.01f)) }
    }
    val targetWidth = l.targetWidth ?: l.secondary
    SliderRow(stringResource(Res.string.style_target_weight), targetWidth, 0.5f..4f, px1(targetWidth)) { v ->
        change { it.withTargetWidth(step(v, 0.1f)) }
    }
    ChoiceRow(stringResource(Res.string.style_shape), inline = true) {
        SegmentedControl(
            listOf(stringResource(Res.string.style_smooth), stringResource(Res.string.style_stepped)),
            if (l.stepped) 1 else 0,
            fill = true,
            onSelect = { i -> change { it.withStepped(i == 1) } },
        )
    }
    val looks = listOf(TargetLook.DASHED, TargetLook.DOTTED, TargetLook.FAINT)
    ChoiceRow(stringResource(Res.string.style_targets), inline = true) {
        SegmentedControl(
            listOf(
                stringResource(Res.string.style_dashed),
                stringResource(Res.string.style_dotted),
                stringResource(Res.string.style_faint),
            ),
            looks.indexOf(l.targets),
            fill = true,
            onSelect = { i -> change { it.withTargets(looks[i]) } },
        )
    }
    EffectControls(
        stringResource(Res.string.style_line_shadow),
        l.shadow,
        DefaultLineShadow,
        halo = false,
        ColorTarget(
            Res.string.style_line_shadow.text(),
            { it.lines.shadow?.color ?: 0 },
            { st, c -> st.withLineShadow(st.lines.shadow?.copy(color = c)) },
            withOpacity = true,
        ),
        s,
        pickColor,
    ) { e ->
        change { it.withLineShadow(e) }
    }
}

// Grid ----------------------------------------------------------------------------------------

@Composable
internal fun GridControls(s: GraphStyle, change: Change, pickColor: Pick) {
    val modes = listOf(GridMode.NONE, GridMode.ROWS, GridMode.BOTH)
    ChoiceRow(stringResource(Res.string.style_grid)) {
        SegmentedControl(
            listOf(
                stringResource(Res.string.style_none),
                stringResource(Res.string.style_rows),
                stringResource(Res.string.style_rows_and_columns),
            ),
            modes.indexOf(s.grid.mode),
            fill = true,
            onSelect = { i -> change { it.withGrid(modes[i]) } },
            tags = modes.map { TestTags.choice("grid", it.name) },
        )
    }
    if (s.grid.mode != GridMode.NONE) {
        ColorRow(
            stringResource(Res.string.style_colour),
            ColorTarget(
                Res.string.style_grid_colour.text(),
                { it.grid.color },
                { st, c -> st.withGridColor(c) },
                withOpacity = true,
            ),
            s,
            pickColor,
        )
        StyleRow(stringResource(Res.string.style_dashed)) {
            CgToggle(s.grid.dash != null, onCheckedChange = { on -> change { it.withGridDashed(on) } })
        }
    }
}

// Text ----------------------------------------------------------------------------------------

@Composable
internal fun TextControls(s: GraphStyle, change: Change, pickColor: Pick) {
    val fonts = listOf(OverlayFont.SANS, OverlayFont.MONO)
    ChoiceRow(stringResource(Res.string.style_font)) {
        SegmentedControl(
            listOf(stringResource(Res.string.style_font_sans), stringResource(Res.string.style_font_mono)),
            fonts.indexOf(s.text.font),
            fill = true,
            onSelect = { i -> change { it.withFont(fonts[i]) } },
            tags = fonts.map { TestTags.choice("font", it.name) },
        )
    }
    val letterings = listOf(Lettering.WORDS, Lettering.READOUT)
    ChoiceRow(stringResource(Res.string.style_lettering)) {
        SegmentedControl(
            listOf(
                stringResource(Res.string.style_lettering_words),
                stringResource(Res.string.style_lettering_readout),
            ),
            letterings.indexOf(s.text.lettering),
            fill = true,
            onSelect = { i -> change { it.withLettering(letterings[i]) } },
            tags = letterings.map { TestTags.choice("lettering", it.name) },
        )
    }
    ColorList {
        ColorListRow(
            stringResource(Res.string.style_title),
            ColorTarget(
                Res.string.style_title_colour.text(),
                { it.text.title },
                { st, c -> st.withTitleColor(c) },
                withOpacity = true,
            ),
            s,
            pickColor,
        )
        ColorListRow(
            stringResource(Res.string.style_labels),
            ColorTarget(
                Res.string.style_label_colour.text(),
                { it.text.label },
                { st, c -> st.withLabelColor(c) },
                withOpacity = true,
            ),
            s,
            pickColor,
        )
        ColorListRow(
            stringResource(Res.string.style_target_values),
            ColorTarget(
                Res.string.style_target_value_colour.text(),
                { it.text.target },
                { st, c -> st.withTargetTextColor(c) },
                true,
            ),
            s,
            pickColor,
        )
        ColorListRow(
            stringResource(Res.string.style_phase_lines),
            ColorTarget(
                Res.string.style_phase_line_colour.text(),
                { it.phase.line },
                { st, c -> st.withPhaseLineColor(c) },
                withOpacity = true,
            ),
            s,
            pickColor,
        )
        ColorListRow(
            stringResource(Res.string.style_phase_names),
            ColorTarget(
                Res.string.style_phase_name_colour.text(),
                { it.phase.label },
                { st, c -> st.withPhaseLabelColor(c) },
                true,
            ),
            s,
            pickColor,
        )
    }
    EffectControls(
        stringResource(Res.string.style_under_the_text),
        s.text.effect,
        DefaultTextShadow,
        halo = true,
        ColorTarget(
            Res.string.style_text_shadow.text(),
            { it.text.effect?.color ?: 0 },
            { st, c -> st.withTextEffect(st.text.effect?.copy(color = c)) },
            withOpacity = true,
        ),
        s,
        pickColor,
    ) { e ->
        change { it.withTextEffect(e) }
    }
}

// Readings ------------------------------------------------------------------------------------

@Composable
internal fun ReadingControls(s: GraphStyle, luma: Float, change: Change, pickColor: Pick) {
    val looks = buildList {
        addAll(listOf(CellLook.FILLED, CellLook.OUTLINED, CellLook.PLAIN, CellLook.GLOSSY))
        if (s.panel.tiles != null) add(CellLook.TILES)
    }
    ChoiceRow(stringResource(Res.string.style_cells)) {
        SegmentedControl(
            looks.map { stringResource(it.label) },
            looks.indexOf(s.cells.look),
            fill = true,
            onSelect = { i -> change { it.withCellLook(looks[i]) } },
        )
    }
    val c = s.cells
    when (c.look) {
        CellLook.FILLED -> {
            ColorRow(
                stringResource(Res.string.style_cell_colour),
                ColorTarget(
                    Res.string.style_cell_colour.text(),
                    { it.cells.fill },
                    { st, v -> st.withCellFill(v) },
                    withOpacity = true,
                ),
                s,
                pickColor,
            )
            CellBorder(s, change, pickColor)
        }
        CellLook.OUTLINED -> CellBorder(s, change, pickColor, toggle = false)
        CellLook.GLOSSY -> {
            GlossControls(
                Res.string.style_gloss,
                Gloss.of(c.gradient),
                fallback = { Gloss.around(s.panel.color) },
                s,
                change,
                pickColor,
                get = { Gloss.of(it.cells.gradient) },
                set = { st, g -> st.withCellGloss(g) },
                toggle = false,
            )
            CellBorder(s, change, pickColor)
        }
        CellLook.TILES ->
            for (r in Reading.entries) {
                ColorRow(
                    stringResource(Res.string.style_x_tile, r.label()),
                    ColorTarget(
                        Res.string.style_x_tile.text(r.label()),
                        { it.cells.tiles[r] ?: 0 },
                        { st, v -> st.withTileColor(r, v) },
                    ),
                    s,
                    pickColor,
                )
            }
        CellLook.PLAIN -> Unit
    }
    val mode =
        when (c.values) {
            s.withSeriesValues().cells.values -> 0
            s.withWhiteValues().cells.values -> 1
            else -> 2
        }
    var custom by remember { mutableStateOf(mode == 2) }
    ChoiceRow(stringResource(Res.string.style_values)) {
        SegmentedControl(
            listOf(
                stringResource(Res.string.style_series_colours),
                stringResource(Res.string.style_white),
                stringResource(Res.string.style_custom),
            ),
            if (custom) 2 else mode,
            fill = true,
            onSelect = { i ->
                custom = i == 2
                when (i) {
                    0 -> change { it.withSeriesValues() }
                    1 -> change { it.withWhiteValues() }
                }
            },
        )
    }
    if (custom || mode == 2) {
        for (r in Reading.entries) {
            ColorRow(
                stringResource(Res.string.style_x_value, r.label()),
                ColorTarget(
                    Res.string.style_x_value.text(r.label()),
                    { it.cells.values[r] ?: WHITE },
                    { st, v -> st.withValueColor(r, v) },
                ),
                s,
                pickColor,
            )
        }
    }
    val weights = listOf(300, 400, 600)
    ChoiceRow(stringResource(Res.string.style_value_weight)) {
        SegmentedControl(
            listOf(
                stringResource(Res.string.style_light),
                stringResource(Res.string.style_regular),
                stringResource(Res.string.style_semibold),
            ),
            weights.indexOf(c.valueWeight).coerceAtLeast(0),
            fill = true,
            onSelect = { i -> change { it.withValueWeight(weights[i]) } },
        )
    }
    SliderRow(stringResource(Res.string.style_corners), c.radius, 0f..16f, px(c.radius)) { v ->
        change { it.withCellRadius(v.roundToInt().toFloat()) }
    }
    StyleRow(stringResource(Res.string.style_adapt_to_video)) {
        CgToggle(s.adapt.enabled, onCheckedChange = { on -> change { it.withAdapt(on) } })
    }
    if (s.adapt.enabled) {
        More(stringResource(Res.string.style_over_bright_video)) { DarkControls(s, change, pickColor) }
    }
    ReadabilityWarning(s, luma, change)
}

@Composable
private fun CellBorder(s: GraphStyle, change: Change, pickColor: Pick, toggle: Boolean = true) {
    val target =
        ColorTarget(
            Res.string.style_cell_border.text(),
            { it.cells.border },
            { st, v -> st.withCellBorder(v) },
            withOpacity = true,
        )
    if (toggle)
        ToggleColorRow(
            stringResource(Res.string.style_border),
            s.cells.border,
            0x33FFFFFF,
            target,
            s,
            change,
            pickColor,
        )
    else ColorRow(stringResource(Res.string.style_border), target, s, pickColor)
}

/** The darker set adapt to video switches to over bright frames. */
@Composable
private fun DarkControls(s: GraphStyle, change: Change, pickColor: Pick) {
    val dark = s.adapt.dark ?: return
    ColorRow(
        stringResource(Res.string.style_text),
        ColorTarget(
            Res.string.style_text_over_bright_video.text(),
            { it.adapt.dark?.text ?: 0 },
            { st, c -> st.withDarkText(c) },
        ),
        s,
        pickColor,
    )
    for (series in StyleSeries) {
        ColorRow(
            series.label,
            ColorTarget(
                Res.string.style_x_over_bright_video.text(series.labelRes.text()),
                { it.adapt.dark?.series?.of(series) ?: 0 },
                { st, c -> st.withDarkSeries(series, c) },
            ),
            s,
            pickColor,
        )
    }
    EffectControls(
        stringResource(Res.string.style_under_the_text),
        dark.effect,
        DefaultHalo,
        halo = true,
        ColorTarget(
            Res.string.style_text_halo_over_bright_video.text(),
            { it.adapt.dark?.effect?.color ?: 0 },
            { st, c -> st.withDarkEffect(st.adapt.dark?.effect?.copy(color = c)) },
            withOpacity = true,
        ),
        s,
        pickColor,
    ) { e ->
        change { it.withDarkEffect(e) }
    }
    StyleRow(stringResource(Res.string.style_scrim)) {
        CgToggle(s.adapt.scrim, onCheckedChange = { on -> change { it.withScrim(on) } })
    }
}

// Shared rows ---------------------------------------------------------------------------------

/** A control's name as the style editor sets it: small Mono capitals in secondary grey. */
@Composable
internal fun StyleLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.caps(), style = CgTheme.type.data, color = CgColors.Text.Secondary, modifier = modifier)
}

/** A setting of the style: its name on the left, its control (a switch, a colour) on the right; 44 dp tall at least. */
@Composable
private fun StyleRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = CgSize.Touch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        StyleLabel(label, Modifier.weight(1f))
        control()
    }
}

/**
 * A choice of the style: its name above, the segments across the whole width under it; [inline] puts the name on the
 * left, as wide as a slider's, and the segments beside it (the lines page, where it sits among sliders).
 */
@Composable
private fun ChoiceRow(label: String, inline: Boolean = false, segments: @Composable () -> Unit) {
    if (inline) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        ) {
            StyleLabel(label, Modifier.width(SLIDER_LABEL))
            Box(Modifier.weight(1f)) { segments() }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CgSpace.S6)) {
            StyleLabel(label)
            segments()
        }
    }
}

/** Rows of colours in one hairline frame, a divider between each two, as the canvas lists a page's colours. */
@Composable
private fun ColorList(rows: @Composable ColorListScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Line.Hairline)) { ColorListScope().rows() }
}

/** Counts [ColorList]'s rows as they compose, so each but the first draws the divider above it. */
private class ColorListScope {
    private var count = 0

    fun first(): Boolean = count++ == 0
}

/**
 * One colour of a [ColorList]: its name, its [swatch] and the colour's name in grey; the whole row opens the colour
 * sheet.
 */
@Composable
private fun ColorListScope.ColorListRow(
    label: String,
    name: String,
    swatch: @Composable () -> Unit,
    height: Dp = CgSize.Touch,
    onClick: () -> Unit,
) {
    val top = first()
    Row(
        Modifier.fillMaxWidth()
            .height(height)
            .drawBehind { if (!top) drawRect(CgColors.Line.Divider, size = Size(size.width, CgStroke.Hairline.toPx())) }
            .clickable(role = Role.Button, onClickLabel = name, onClick = onClick)
            .padding(horizontal = CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        swatch()
        Text(label.caps(), style = CgTheme.type.data, color = CgColors.Text.Primary, modifier = Modifier.weight(1f))
        Text(name.caps(), style = CgTheme.type.data, color = CgColors.Text.Secondary, maxLines = 1)
    }
}

/** A colour of the style in a [ColorList]: a small square of it (at its opacity) before its name. */
@Composable
private fun ColorListScope.ColorListRow(label: String, target: ColorTarget, s: GraphStyle, pickColor: Pick) {
    val c = target.get(s)
    ColorListRow(
        label,
        if (target.withOpacity) tintLabel(c, c.opacity).text() else colorName(c).text(),
        swatch = { Box(Modifier.size(16.dp).background(Color(c)).border(CgStroke.Hairline, CgColors.Line.Hairline)) },
    ) {
        pickColor(target)
    }
}

/** A colour of the style as a chip that opens the colour sheet. */
@Composable
private fun ColorRow(label: String, target: ColorTarget, s: GraphStyle, pickColor: Pick) {
    val c = target.get(s)
    StyleRow(label) {
        ColorChip(
            Color(c),
            if (target.withOpacity) tintLabel(c, c.opacity).text() else colorName(c).text(),
            { pickColor(target) },
        )
    }
}

/** A colour that can be off (0): a switch, and its chip when on. */
@Composable
private fun ToggleColorRow(
    label: String,
    value: Int,
    default: Int,
    target: ColorTarget,
    s: GraphStyle,
    change: Change,
    pickColor: Pick,
) {
    StyleRow(label) {
        Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S8), verticalAlignment = Alignment.CenterVertically) {
            if (value != 0) ColorChip(Color(value), tintLabel(value, value.opacity).text(), { pickColor(target) })
            CgToggle(value != 0, onCheckedChange = { on -> change { target.set(it, if (on) default else 0) } })
        }
    }
}

/**
 * A shadow under something (or, with [halo], a glow around text: a shadow with no offset): none, shadow or halo, then
 * its colour, softness and drop.
 */
@Composable
private fun EffectControls(
    label: String,
    effect: Shadow?,
    default: Shadow,
    halo: Boolean,
    color: ColorTarget,
    s: GraphStyle,
    pickColor: Pick,
    maxBlur: Float = 8f,
    maxOffset: Float = 4f,
    onChange: (Shadow?) -> Unit,
) {
    val kinds =
        (if (halo) listOf(Res.string.style_none, Res.string.style_shadow, Res.string.style_halo)
            else listOf(Res.string.style_none, Res.string.style_on))
            .map { stringResource(it) }
    val kind =
        when {
            effect == null -> 0
            halo && effect.dy == 0f -> 2
            else -> 1
        }
    ChoiceRow(label) {
        SegmentedControl(
            kinds,
            kind,
            fill = true,
            onSelect = { i ->
                onChange(
                    when (i) {
                        0 -> null
                        2 -> (effect ?: DefaultHalo).copy(dy = 0f)
                        else -> (effect ?: default).let { if (it.dy == 0f) it.copy(dy = default.dy) else it }
                    }
                )
            },
        )
    }
    if (effect != null) {
        ColorRow(stringResource(Res.string.style_x_colour, label), color, s, pickColor)
        SliderRow(stringResource(Res.string.style_softness), effect.blur, 0f..maxBlur, px1(effect.blur)) { v ->
            onChange(effect.copy(blur = step(v, 0.5f)))
        }
        if (effect.dy != 0f || !halo) {
            SliderRow(stringResource(Res.string.style_drop), effect.dy, 0f..maxOffset, px1(effect.dy)) { v ->
                onChange(effect.copy(dy = step(v, 0.5f)))
            }
        }
    }
}

/** A two-tone gloss: [toggle] it on (from [fallback]), then its four colours and where the step is. */
@Composable
private fun GlossControls(
    label: StringResource,
    gloss: Gloss?,
    fallback: () -> Gloss,
    s: GraphStyle,
    change: Change,
    pickColor: Pick,
    get: (GraphStyle) -> Gloss?,
    set: (GraphStyle, Gloss?) -> GraphStyle,
    toggle: Boolean = true,
) {
    if (toggle) {
        StyleRow(stringResource(label)) {
            CgToggle(gloss != null, onCheckedChange = { on -> change { set(it, if (on) fallback() else null) } })
        }
    }
    val g = gloss ?: return
    fun part(name: StringResource, read: (Gloss) -> Int, write: Gloss.(Int) -> Gloss) =
        ColorTarget(
            Res.string.style_gloss_part.text(label.text(), name.text()),
            { st -> get(st)?.let(read) ?: 0 },
            { st, c -> get(st)?.let { set(st, it.write(c)) } ?: st },
            withOpacity = true,
        )
    @Composable
    fun Part(name: StringResource, read: (Gloss) -> Int, write: Gloss.(Int) -> Gloss) =
        ColorRow(stringResource(name), part(name, read, write), s, pickColor)
    Part(Res.string.style_gloss_top, { it.topFrom }) { copy(topFrom = it) }
    Part(Res.string.style_gloss_above, { it.topTo }) { copy(topTo = it) }
    Part(Res.string.style_gloss_below, { it.bottomFrom }) { copy(bottomFrom = it) }
    Part(Res.string.style_gloss_bottom, { it.bottomTo }) { copy(bottomTo = it) }
    SliderRow(stringResource(Res.string.style_step_at), g.split, 0.1f..0.9f, percent(g.split)) { v ->
        change { st -> get(st)?.let { set(st, it.copy(split = step(v, 0.01f))) } ?: st }
    }
}

/** A tab's rarely needed controls, folded away until asked for. */
@Composable
private fun More(label: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    // The arrow turns down as the rows open under it, and back as they fold away.
    val turn by animateFloatAsState(if (open) 90f else 0f, CgMotion.settle(), label = "arrow")
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = CgSize.Touch)
            .border(CgStroke.Hairline, CgColors.Line.Hairline)
            .clickable(role = Role.Button) { open = !open }
            .padding(horizontal = CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CgIcon(CgIcons.ChevronRight, Modifier.graphicsLayer { rotationZ = turn }, size = 16.dp)
        Text(
            label.caps(),
            style = CgTheme.type.labelStrong,
            color = CgColors.Text.Primary,
            modifier = Modifier.padding(start = CgSpace.S6),
        )
    }
    AnimatedVisibility(
        open,
        enter = CgMotion.reveal,
        exit = CgMotion.conceal,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) { content() }
    }
}

/** [v] to the nearest multiple of [by], as the float a literal of that value is (2.6, not 2.6000001). */
internal fun step(v: Float, by: Float): Float {
    val per = kotlin.math.round(1f / by)
    return kotlin.math.round(v * per) / per
}

private fun percent(v: Float) = "${(v * 100).roundToInt()}%"

private fun px(v: Float) = "${v.roundToInt()} px"

private fun px1(v: Float) = "${(v * 10).roundToInt() / 10f} px"

/** The cells' look as the segmented control names it. */
private val CellLook.label: StringResource
    get() =
        when (this) {
            CellLook.FILLED -> Res.string.style_cell_filled
            CellLook.OUTLINED -> Res.string.style_cell_outlined
            CellLook.PLAIN -> Res.string.style_cell_plain
            CellLook.GLOSSY -> Res.string.style_cell_glossy
            CellLook.TILES -> Res.string.style_cell_tiles
        }

/** A curve's row in the lines page's colour list. */
private val SERIES_ROW = 40.dp
