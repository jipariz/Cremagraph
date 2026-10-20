package app.onlyshots.edit

import androidx.compose.runtime.staticCompositionLocalOf
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.edit_desktop_footer
import app.onlyshots.core.resources.edit_desktop_save
import app.onlyshots.core.resources.edit_desktop_save_where
import app.onlyshots.core.resources.edit_desktop_saved
import app.onlyshots.core.resources.edit_desktop_share
import app.onlyshots.core.resources.edit_desktop_share_where
import app.onlyshots.core.resources.edit_desktop_shared
import app.onlyshots.core.resources.edit_open_another
import app.onlyshots.core.resources.edit_phone_footer
import app.onlyshots.core.resources.edit_phone_next
import app.onlyshots.core.resources.edit_phone_save
import app.onlyshots.core.resources.edit_phone_save_where
import app.onlyshots.core.resources.edit_phone_saved
import app.onlyshots.core.resources.edit_phone_share
import app.onlyshots.core.resources.edit_phone_share_where
import app.onlyshots.core.resources.edit_phone_shared
import app.onlyshots.core.resources.edit_web_footer
import app.onlyshots.core.resources.edit_web_save
import app.onlyshots.core.resources.edit_web_save_where
import app.onlyshots.core.resources.edit_web_saved
import org.jetbrains.compose.resources.StringResource

/**
 * What the export sheet calls its two ways out, per host: the phones' gallery and share sheet, the desktop's Movies
 * folder and Finder, the browser's download. A null [share] leaves that row out.
 */
data class ExportWording(
    val save: StringResource,
    val saveWhere: StringResource,
    val share: StringResource?,
    val shareWhere: StringResource? = null,
    /** Where the video is once [save] finished. */
    val saved: StringResource,
    /** What happened once [share] finished; null says nothing. */
    val shared: StringResource? = null,
    val next: StringResource,
    val footer: StringResource,
    /** Whether this host's export carries sound at all. */
    val sound: Boolean = true,
) {
    companion object {
        val Phone =
            ExportWording(
                save = Res.string.edit_phone_save,
                saveWhere = Res.string.edit_phone_save_where,
                share = Res.string.edit_phone_share,
                shareWhere = Res.string.edit_phone_share_where,
                saved = Res.string.edit_phone_saved,
                shared = Res.string.edit_phone_shared,
                next = Res.string.edit_phone_next,
                footer = Res.string.edit_phone_footer,
            )

        val Desktop =
            ExportWording(
                save = Res.string.edit_desktop_save,
                saveWhere = Res.string.edit_desktop_save_where,
                share = Res.string.edit_desktop_share,
                shareWhere = Res.string.edit_desktop_share_where,
                saved = Res.string.edit_desktop_saved,
                shared = Res.string.edit_desktop_shared,
                next = Res.string.edit_open_another,
                footer = Res.string.edit_desktop_footer,
            )

        /** The browser's export is 1080p and silent, and its one way out is the download. */
        val Web =
            ExportWording(
                save = Res.string.edit_web_save,
                saveWhere = Res.string.edit_web_save_where,
                share = null,
                saved = Res.string.edit_web_saved,
                next = Res.string.edit_open_another,
                footer = Res.string.edit_web_footer,
                sound = false,
            )
    }
}

val LocalExportWording = staticCompositionLocalOf { ExportWording.Phone }
