package app.onlyshots.core.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Text for the user, decided outside composition (view models, the screens' pure rules) and put into words where it is
 * shown: [text] in a composable, [resolve] elsewhere. An argument may itself be a [UiText]; anything else is formatted
 * as it is. Equal values say the same thing, which is what tests compare.
 */
sealed interface UiText {
    /** A string resource with its arguments (`%1$s`…). */
    data class Res(val res: StringResource, val args: List<Any> = emptyList()) : UiText

    /** A plural resource for [count], with its arguments. */
    data class Plural(val res: PluralStringResource, val count: Int, val args: List<Any> = emptyList()) : UiText

    /** Words that are not ours: a file name, a machine's name, an error from the platform. */
    data class Raw(val value: String) : UiText

    /** Parts joined by [separator], each resolved on its own. */
    data class Joined(val parts: List<UiText>, val separator: String = " · ") : UiText
}

fun StringResource.text(vararg args: Any): UiText = UiText.Res(this, args.toList())

fun PluralStringResource.text(count: Int, vararg args: Any): UiText = UiText.Plural(this, count, args.toList())

fun String.raw(): UiText = UiText.Raw(this)

/** This text in words, in composition. */
@Composable
fun UiText.text(): String =
    when (this) {
        is UiText.Res -> stringResource(res, *args.map { it.textArg() }.toTypedArray())
        is UiText.Plural -> pluralStringResource(res, count, *args.map { it.textArg() }.toTypedArray())
        is UiText.Raw -> value
        is UiText.Joined -> parts.map { it.text() }.joinToString(separator)
    }

/** This text in words, outside composition. */
suspend fun UiText.resolve(): String =
    when (this) {
        is UiText.Res -> getString(res, *args.map { it.resolveArg() }.toTypedArray())
        is UiText.Plural -> getPluralString(res, count, *args.map { it.resolveArg() }.toTypedArray())
        is UiText.Raw -> value
        is UiText.Joined -> parts.map { it.resolve() }.joinToString(separator)
    }

@Composable private fun Any.textArg(): Any = if (this is UiText) text() else this

private suspend fun Any.resolveArg(): Any = if (this is UiText) resolve() else this
