package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.transfer.ConflictResolution

@Composable
fun ConflictDialog(
    currentResolution: ConflictResolution,
    onResolutionSelected: (ConflictResolution) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentResolution) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("File Conflict Policy")
        },
        text = {
            Column {
                Text(
                    text = "Choose how SSHDrop handles existing files at the destination during transfer:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                ConflictOptionRow(
                    title = "Overwrite",
                    description = "Replace the existing file with the new one",
                    selected = selected == ConflictResolution.OVERWRITE,
                    onClick = { selected = ConflictResolution.OVERWRITE }
                )

                ConflictOptionRow(
                    title = "Keep Both (Auto-Rename)",
                    description = "Rename the transferred file with (1), (2), etc.",
                    selected = selected == ConflictResolution.KEEP_BOTH,
                    onClick = { selected = ConflictResolution.KEEP_BOTH }
                )

                ConflictOptionRow(
                    title = "Skip",
                    description = "Do not transfer if destination file already exists",
                    selected = selected == ConflictResolution.SKIP,
                    onClick = { selected = ConflictResolution.SKIP }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onResolutionSelected(selected)
                    onDismiss()
                },
                modifier = Modifier.testTag("apply_conflict_policy_button")
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConflictOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
