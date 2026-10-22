package app.onlyshots

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import app.onlyshots.core.designsystem.CgMotion

/*
 * How screens move, for every host's NavDisplay: a screen slides in over the last and stops (iOS keeps its own push,
 * which its users know), sheet-like screens ([ModalMotion]) rise from the bottom, and elements shared between two
 * screens ([shareBounds]) fly from one to the other. Short, one curve, no bounce (CgMotion).
 */

/** iOS: the platform's push and its edge swipe back, as Navigation 3 gives them. Elsewhere, [ScreenMotion]'s own. */
internal expect val nativeScreenMotion: Boolean

/**
 * The forward, back and predictive-back transitions every [NavDisplay] in the app passes: the new screen slides in over
 * the old across the whole width and stops; the old one steps a quarter aside under it. No fades.
 */
object ScreenMotion {
    private const val UNDER = 4 // the screen underneath moves a quarter of the width

    private fun <T> run() = tween<T>(CgMotion.LONG, easing = CgMotion.Mechanical)

    fun <T : Any> forward(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        if (nativeScreenMotion) defaultTransitionSpec()
        else {
            { slideInHorizontally(run()) { it } togetherWith slideOutHorizontally(run()) { -it / UNDER } }
        }

    fun <T : Any> back(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        if (nativeScreenMotion) defaultPopTransitionSpec()
        else {
            {
                (slideInHorizontally(run()) { -it / UNDER } togetherWith slideOutHorizontally(run()) { it }).apply {
                    targetContentZIndex = -1f
                }
            }
        }

    fun <T : Any> predictiveBack(): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform =
        if (nativeScreenMotion) defaultPredictivePopTransitionSpec()
        else {
            { _ ->
                (slideInHorizontally(tween(CgMotion.LONG, easing = CgMotion.Linear)) { -it / UNDER } togetherWith
                        slideOutHorizontally(tween(CgMotion.LONG, easing = CgMotion.Linear)) { it })
                    .apply { targetContentZIndex = -1f }
            }
        }
}

/**
 * Entry metadata for a screen that is more a sheet than a step: it rises from the bottom over the one that opened it,
 * which dims at once, and drops back down on back (the privacy policy, the machine shot picker).
 */
val ModalMotion: Map<String, Any> =
    NavDisplay.transitionSpec {
        slideInVertically(tween(CgMotion.LONG, easing = CgMotion.Mechanical)) { it } togetherWith
            fadeOut(tween(CgMotion.SHORT), targetAlpha = 0.6f)
    } +
        NavDisplay.popTransitionSpec {
            fadeIn(tween(CgMotion.SHORT), initialAlpha = 0.6f) togetherWith
                slideOutVertically(tween(CgMotion.MEDIUM, easing = CgMotion.Mechanical)) { it }
        } +
        NavDisplay.predictivePopTransitionSpec {
            fadeIn(tween(CgMotion.LONG, easing = CgMotion.Linear), initialAlpha = 0.6f) togetherWith
                slideOutVertically(tween(CgMotion.LONG, easing = CgMotion.Linear)) { it }
        }

/**
 * Entry metadata for a screen that grows out of something on the one before it (the style editor out of a style's
 * tile): a quick cross-fade under it, so the shared element is the only thing that travels.
 */
val FadeMotion: Map<String, Any> =
    NavDisplay.transitionSpec { fadeIn(tween(CgMotion.SHORT)) togetherWith fadeOut(tween(CgMotion.SHORT)) } +
        NavDisplay.popTransitionSpec { fadeIn(tween(CgMotion.SHORT)) togetherWith fadeOut(tween(CgMotion.SHORT)) }

/** The scope shared elements fly in: the host's [SharedTransitionLayout], or none (a preview, a screenshot test). */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * The screen's own enter/exit, from its NavDisplay entry ([rememberNavMotionDecorator]); null outside one, where
 * [LocalNavAnimatedContentScope] would throw.
 */
val LocalScreenVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The host's root: elements marked with [shareBounds] on two screens fly between them while the screens change. */
@Composable
fun SharedMotion(content: @Composable () -> Unit) {
    SharedTransitionLayout { CompositionLocalProvider(LocalSharedTransitionScope provides this) { content() } }
}

/** Hands each entry its enter/exit scope as [LocalScreenVisibility], so screens can share elements safely. */
@Composable
fun <T : Any> rememberNavMotionDecorator(): NavEntryDecorator<T> = remember {
    NavEntryDecorator { entry ->
        CompositionLocalProvider(LocalScreenVisibility provides LocalNavAnimatedContentScope.current) {
            entry.Content()
        }
    }
}

/**
 * Marks this node as the same thing as the node with [key] on the other screen (or the other state of an
 * AnimatedContent, given its [visibility]): while the two change places, its bounds morph from one to the other and the
 * two contents cross-fade inside. Nothing outside a [SharedMotion].
 *
 * [overlay] false keeps it drawn in its own screen's layer rather than above both: needed for anything over a video or
 * a camera, whose blur (Haze) and platform view would not follow it into the overlay.
 */
@Composable
fun Modifier.shareBounds(
    key: Any,
    visibility: AnimatedVisibilityScope? = LocalScreenVisibility.current,
    clip: Shape = RectangleShape,
    overlay: Boolean = true,
): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = visibility ?: return this
    return with(shared) {
        this@shareBounds.sharedBounds(
            rememberSharedContentState(key),
            scope,
            boundsTransform = SharedBoundsMotion,
            clipInOverlayDuringTransition = OverlayClip(clip),
            renderInOverlayDuringTransition = overlay,
        )
    }
}

/** Keys of the elements screens share, so both sides name them the same way. */
object SharedKeys {
    /** A graph style's tile in the picker and the style editor's preview of it. */
    fun style(id: String): Any = "style-$id"

    /** A video's picture: its thumbnail where it was picked, the editor's frame. */
    fun video(id: String): Any = "video-$id"

    /** The frame being filmed on the record screen, which becomes the editor's frame once saved. */
    const val TAKE_FRAME = "take-frame"
}

private val SharedBoundsMotion = BoundsTransform { _, _ -> tween(CgMotion.LONG, easing = CgMotion.Mechanical) }
