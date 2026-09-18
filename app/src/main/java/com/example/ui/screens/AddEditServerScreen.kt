package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditServerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val editingServer by viewModel.editingServer.collectAsStateWithLifecycle()

    var name by remember(editingServer) { mutableStateOf(editingServer?.name ?: "") }
    var host by remember(editingServer) { mutableStateOf(editingServer?.host ?: "") }
    var portText by remember(editingServer) { mutableStateOf(editingServer?.port?.toString() ?: "22") }
    var username by remember(editingServer) { mutableStateOf(editingServer?.username ?: "ubuntu") }
    var authType by remember(editingServer) { mutableStateOf(editingServer?.authType ?: "PASSWORD") }
    var password by remember(editingServer) { mutableStateOf(editingServer?.password ?: "") }
    var keyUri by remember(editingServer) { mutableStateOf(editingServer?.keyUri) }
    var keyFileName by remember(editingServer) { mutableStateOf(editingServer?.keyFileName) }
    var keyPassphrase by remember(editingServer) { mutableStateOf(editingServer?.keyPassphrase ?: "") }
    var initialRemoteDir by remember(editingServer) { mutableStateOf(editingServer?.initialRemoteDir ?: "/home") }

    var passwordVisible by remember { mutableStateOf(false) }

    val keyFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            keyUri = uri.toString()
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "id_rsa"
            keyFileName = fileName
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (editingServer != null) "Edit Linux Server" else "Add Linux Server",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.SERVERS) },
                        modifier = Modifier.testTag("back_from_add_server_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Details Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SERVER INFORMATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server Nickname (e.g. My Ubuntu VPS)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_name_input"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host / IP Address") },
                            placeholder = { Text("192.168.1.100 or myserver.com") },
                            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("server_host_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(0.3f)
                                .testTag("server_port_input"),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("SSH Username") },
                        placeholder = { Text("ubuntu, debian, or root") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_username_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = initialRemoteDir,
                        onValueChange = { initialRemoteDir = it },
                        label = { Text("Default Remote Directory") },
                        placeholder = { Text("/home or /var/www") },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_remote_dir_input"),
                        singleLine = true
                    )
                }
            }

            // Authentication Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "AUTHENTICATION METHOD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = authType == "PASSWORD",
                            onClick = { authType = "PASSWORD" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { SegmentedButtonDefaults.Icon(active = authType == "PASSWORD") },
                            modifier = Modifier.testTag("auth_password_toggle")
                        ) {
                            Text("Password")
                        }

                        SegmentedButton(
                            selected = authType == "KEY",
                            onClick = { authType = "KEY" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { SegmentedButtonDefaults.Icon(active = authType == "KEY") },
                            modifier = Modifier.testTag("auth_key_toggle")
                        ) {
                            Text("Private Key")
                        }
                    }

                    if (authType == "PASSWORD") {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("SSH Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_password_input"),
                            singleLine = true
                        )
                    } else {
                        // Key picker via SAF
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { keyFilePickerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pick_key_file_button")
                            ) {
                                Icon(imageVector = Icons.Default.Key, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (!keyFileName.isNullOrEmpty()) "Selected: $keyFileName" else "Import Private Key (SAF)"
                                )
                            }

                            Text(
                                text = "Supports RSA, ED25519, ECDSA (.pem, id_rsa, OpenSSH format)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = keyPassphrase,
                                onValueChange = { keyPassphrase = it },
                                label = { Text("Key Passphrase (Optional)") },
                                placeholder = { Text("Leave blank if unencrypted") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("key_passphrase_input"),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            val isFormValid = host.isNotBlank() && username.isNotBlank()
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 22
                    viewModel.saveServer(
                        name = name,
                        host = host,
                        port = port,
                        username = username,
                        authType = authType,
                        password = if (authType == "PASSWORD") password else null,
                        keyUri = if (authType == "KEY") keyUri else null,
                        keyFileName = if (authType == "KEY") keyFileName else null,
                        keyPassphrase = if (authType == "KEY") keyPassphrase else null,
                        initialRemoteDir = initialRemoteDir
                    )
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_server_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (editingServer != null) "Update Server" else "Save Server",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
