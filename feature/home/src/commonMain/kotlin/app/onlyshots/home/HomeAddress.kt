package app.onlyshots.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgButtonKind
import app.onlyshots.core.designsystem.component.CgSheet
import app.onlyshots.core.designsystem.component.CgTextField
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_add_by_address
import app.onlyshots.core.resources.home_address_label
import app.onlyshots.core.resources.home_address_note
import app.onlyshots.core.resources.home_connect
import app.onlyshots.layout.FoldPage
import org.jetbrains.compose.resources.stringResource

/*
 * Adding a machine by its address: a sheet, or on a book foldable the right page.
 */

@Composable
internal fun AddressSheet(address: String, onAddress: (String) -> Unit, onConnect: () -> Unit, onDismiss: () -> Unit) {
    CgSheet(title = stringResource(Res.string.home_add_by_address), onDismiss = onDismiss) {
        AddressForm(address, onAddress, onConnect)
    }
}

/** The address sheet as a book foldable's right page. */
@Composable
internal fun AddressPage(
    address: String,
    onAddress: (String) -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoldPage(stringResource(Res.string.home_add_by_address), onDismiss, modifier) {
        Box(Modifier.padding(CgSpace.S16)) { AddressForm(address, onAddress, onConnect) }
    }
}

/** What adding a machine by its address asks for: the note, the address and connect. */
@Composable
private fun AddressForm(address: String, onAddress: (String) -> Unit, onConnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S16)) {
        Text(
            stringResource(Res.string.home_address_note),
            style = CgTheme.type.bodySmall,
            color = CgColors.Text.Secondary,
        )
        val label = stringResource(Res.string.home_address_label)
        Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S6)) {
            SectionLabel(label)
            CgTextField(
                value = address,
                onValueChange = onAddress,
                label = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onConnect() }),
                modifier = Modifier.fillMaxWidth().testTag(TestTags.HomeAddressField),
            )
        }
        CgButton(
            stringResource(Res.string.home_connect),
            onClick = onConnect,
            kind = CgButtonKind.Signal,
            enabled = address.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag(TestTags.HomeAddressConnect),
        )
    }
}
