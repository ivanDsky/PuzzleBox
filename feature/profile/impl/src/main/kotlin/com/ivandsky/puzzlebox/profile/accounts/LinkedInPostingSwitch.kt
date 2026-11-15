package com.ivandsky.puzzlebox.profile.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalWarning
import com.ivandsky.puzzlebox.profile.linkedin.LinkedInPostingWarning

private val MinRowHeight = 48.dp

/**
 * The "Post results to LinkedIn" switch with its Experimental label, shown on the LinkedIn row while posting is
 * offered. [checked] is the stored choice. Turning it on opens [LinkedInPostingWarning] and reports nothing until
 * Accept, so [onCheckedChange] gets true only after the player accepted; Cancel leaves everything as it was. Turning
 * it off reports false at once, with no confirmation. The whole row is the tap target; the switch only shows state.
 */
@Composable
internal fun LinkedInPostingSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinRowHeight)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { on -> if (on) asking = true else onCheckedChange(false) },
            )
            .testTag("profile.linkedin.posting"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Post results to LinkedIn",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Experimental",
                style = MaterialTheme.typography.bodySmall,
                color = LocalWarning.current,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
    if (asking) {
        LinkedInPostingWarning(
            onAccept = {
                asking = false
                onCheckedChange(true)
            },
            onCancel = { asking = false },
        )
    }
}
