package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun ExitWarningDialog(
    onKeepAppOpen: () -> Unit,
    onCancelAndLeave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepAppOpen,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Transfer in Progress")
        },
        text = {
            Text(
                "A file transfer is currently running in the foreground. Because SSHDrop operates strictly without background services for security and privacy, navigating away will cancel the active transfer.\n\nPlease keep the app open to finish transferring."
            )
        },
        confirmButton = {
            Button(
                onClick = onKeepAppOpen,
                modifier = Modifier.testTag("keep_app_open_button")
            ) {
                Text("Keep App Open")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancelAndLeave,
                modifier = Modifier.testTag("cancel_transfer_leave_button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Cancel Transfer & Leave")
            }
        }
    )
}
