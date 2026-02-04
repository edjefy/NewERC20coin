package com.raybans.claudeassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.raybans.claudeassistant.ui.theme.ClaudeAssistantTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Settings screen for configuring the Claude Assistant app
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ClaudeAssistantTheme {
                SettingsScreen(
                    onBack = { finish() },
                    getApiKey = {
                        var key = ""
                        lifecycleScope.launch {
                            key = dataStore.data.map { it[API_KEY] ?: "" }.first()
                        }
                        key
                    },
                    onSaveApiKey = { key ->
                        lifecycleScope.launch {
                            dataStore.edit { it[API_KEY] = key }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    getApiKey: () -> String,
    onSaveApiKey: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apiKey = getApiKey()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instellingen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Terug")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // API Key Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Claude API",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Voer je Anthropic API key in om Claude te kunnen gebruiken via je Ray-Bans.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-ant-api03-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showKey) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "Verberg" else "Toon")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Haal je API key op bij console.anthropic.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onSaveApiKey(apiKey) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Opslaan")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Usage instructions
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Hoe te gebruiken",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    InstructionItem(
                        title = "Praten met Claude",
                        description = "Tik op de touchpad van je Ray-Bans of druk op de microfoonknop in de app."
                    )
                    InstructionItem(
                        title = "Laatste antwoord herhalen",
                        description = "Veeg naar voren op je Ray-Bans of tik op 'Herhaal' in de app."
                    )
                    InstructionItem(
                        title = "Nieuw gesprek",
                        description = "Veeg naar achteren op je Ray-Bans of tik op 'Nieuw gesprek' om de geschiedenis te wissen."
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Over deze app",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Claude Ray-Bans Assistant v1.0\n\nGebruik Claude handsfree via je Meta Ray-Bans smart glasses. Deze app luistert naar je spraak, stuurt het naar Claude, en spreekt het antwoord voor via je Ray-Bans.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun InstructionItem(title: String, description: String) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = "• $title",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
