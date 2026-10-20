package app.onlyshots.web

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.onlyshots.EditRoute
import app.onlyshots.OpenRoute
import app.onlyshots.PrivacyRoute
import app.onlyshots.StyleEditorRoute
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import com.github.terrakok.navigation3.browser.HierarchicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentName

/**
 * The browser's back button and history, tied to the editor's back stack (navigation3-browser). On a phone the back
 * button behaves like the app (back one screen, and from the start screen out of the page); on a computer back and
 * forward walk the screens like a website's pages, and the address bar names the screen.
 *
 * An editor or style editor address cannot come back after a reload: the web's videos live only in the tab. Those fall
 * back to the start screen; the start screen and the privacy page come back.
 */
@Composable
fun BrowserHistory(backStack: NavBackStack<NavKey>) {
    if (isMobileBrowser()) {
        HierarchicalBrowserNavigation { backStack.lastOrNull()?.let(::fragment) }
    } else {
        ChronologicalBrowserNavigation(
            backStack = backStack,
            saveKey = ::fragment,
            restoreKey = { fragment ->
                when (getBrowserHistoryFragmentName(fragment)) {
                    "open" -> OpenRoute
                    "privacy" -> PrivacyRoute
                    else -> null
                }
            },
        )
    }
}

/** The address bar's name for a screen. */
private fun fragment(key: NavKey): String? =
    when (key) {
        is OpenRoute -> buildBrowserHistoryFragment("open")
        is PrivacyRoute -> buildBrowserHistoryFragment("privacy")
        is EditRoute -> buildBrowserHistoryFragment("edit", mapOf("id" to key.id))
        is StyleEditorRoute -> buildBrowserHistoryFragment("style")
        else -> null
    }

/** A phone or tablet browser, by the library's suggested check: its own hint, a coarse pointer, or the user agent. */
internal fun isMobileBrowser(): Boolean =
    // An expression (not a block with returns), which both the wasm and the JS build accept.
    js(
        """(() => {
          if (navigator.userAgentData && navigator.userAgentData.mobile) return true;
          if (window.matchMedia && window.matchMedia('(pointer: coarse)').matches) return window.innerWidth <= 820;
          return /Mobi|Android|iPhone|iPad|iPod|IEMobile|Opera Mini|CriOS/i.test(navigator.userAgent);
        })()"""
    )
