package app.onlyshots.open

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.illus_phone_graph
import app.onlyshots.core.resources.illus_store_apple
import app.onlyshots.core.resources.illus_store_play
import app.onlyshots.core.resources.open_band_detail
import app.onlyshots.core.resources.open_banner_title
import app.onlyshots.core.resources.open_store_app_store
import app.onlyshots.core.resources.open_store_app_store_name
import app.onlyshots.core.resources.open_store_app_store_top
import app.onlyshots.core.resources.open_store_play
import app.onlyshots.core.resources.open_store_play_name
import app.onlyshots.core.resources.open_store_play_top
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The phone app in the stores, as the web offers it (the desktop app and the phones offer nothing). [onPlay] and
 * [onAppStore] open the app or its store page, null where that store has nothing for this browser. [phone]: the browser
 * is on a phone, which gets its own compact page with a banner instead of the computer's band; the banner can be closed
 * ([bannerDismissed], remembered by the host).
 */
data class StoreLinks(
    val onPlay: (() -> Unit)?,
    val onAppStore: (() -> Unit)?,
    val phone: Boolean,
    val bannerDismissed: Boolean = false,
    val onDismissBanner: () -> Unit = {},
) {
    val any: Boolean
        get() = onPlay != null || onAppStore != null
}

internal enum class Store {
    Play,
    AppStore,
}

/**
 * A store's button, as the design draws it: black, a grey hairline, the store's mark and its two lines ("GET IT ON /
 * Google Play", "Download on the / App Store").
 */
@Composable
internal fun StoreButton(store: Store, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp) {
    val shape = RoundedCornerShape(12.dp)
    val label = stringResource(if (store == Store.Play) Res.string.open_store_play else Res.string.open_store_app_store)
    Row(
        modifier
            .heightIn(min = height)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, CgColors.Text.Dim, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(start = 16.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (store == Store.Play) PlayMark(Modifier.size(22.dp, 24.dp)) else AppleMark(Modifier.size(20.dp, 24.dp))
        Column {
            Text(
                stringResource(
                    if (store == Store.Play) Res.string.open_store_play_top else Res.string.open_store_app_store_top
                ),
                style = CgTheme.type.body.copy(fontSize = 11.sp, lineHeight = 12.sp, letterSpacing = 0.03.em),
                color = CgColors.Series.Weight,
            )
            Text(
                stringResource(
                    if (store == Store.Play) Res.string.open_store_play_name else Res.string.open_store_app_store_name
                ),
                style = CgTheme.type.body.copy(fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}

/**
 * The computer's (and tablet's) "Get the app" band, under the steps: what the app adds, and a button for each store
 * this browser has one for.
 */
@Composable
internal fun AppBand(stores: StoreLinks, wide: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    val buttons =
        @Composable {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                stores.onPlay?.let { StoreButton(Store.Play, it) }
                stores.onAppStore?.let { StoreButton(Store.AppStore, it) }
            }
        }
    val text =
        @Composable { mod: Modifier ->
            Row(
                mod,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(CgColors.Surface.Raised),
                    Alignment.Center,
                ) {
                    PhoneGraphIcon(Modifier.size(26.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.open_banner_title),
                        style = CgTheme.type.body.copy(fontSize = 18.sp),
                        color = CgColors.Text.Primary,
                    )
                    Text(
                        stringResource(Res.string.open_band_detail),
                        style = CgTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = CgColors.Text.Secondary,
                    )
                }
            }
        }
    val mod =
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(CgColors.Surface.Bar)
            .border(1.dp, CgColors.Line.Hairline, shape)
            .padding(horizontal = 28.dp, vertical = 24.dp)
    if (wide) {
        Row(mod, horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            text(Modifier.weight(1f))
            buttons()
        }
    } else {
        Column(mod, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            text(Modifier)
            buttons()
        }
    }
}

// The stores' marks and the band's icon: vector drawables in :core:resources (from the design's SVG) ---------------

/** The Google Play triangle in its four colours. */
@Composable
internal fun PlayMark(modifier: Modifier) {
    Image(painterResource(Res.drawable.illus_store_play), null, modifier, alignment = Alignment.TopStart)
}

/** The Apple mark, in [color]. */
@Composable
internal fun AppleMark(modifier: Modifier, color: Color = Color.White) {
    Image(
        painterResource(Res.drawable.illus_store_apple),
        null,
        modifier,
        alignment = Alignment.TopStart,
        colorFilter = ColorFilter.tint(color),
    )
}

/** A phone with the graph on it: the band's tile. */
@Composable
private fun PhoneGraphIcon(modifier: Modifier) {
    Image(painterResource(Res.drawable.illus_phone_graph), null, modifier, alignment = Alignment.TopStart)
}
