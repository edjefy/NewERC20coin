package com.raybans.claudeassistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.raybans.claudeassistant.audio.BluetoothAudioHandler
import com.raybans.claudeassistant.service.VoiceAssistantService
import com.raybans.claudeassistant.ui.theme.ClaudeAssistantTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore(name = "settings")
val API_KEY = stringPreferencesKey("api_key")

class MainActivity : ComponentActivity() {

    private var voiceService: VoiceAssistantService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VoiceAssistantService.LocalBinder
            voiceService = binder.getService()
            serviceBound = true

            // Load saved API key
            lifecycleScope.launch {
                val savedKey = dataStore.data.map { it[API_KEY] }.first()
                savedKey?.let { voiceService?.setApiKey(it) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
            serviceBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startVoiceService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            ClaudeAssistantTheme {
                MainScreen(
                    getService = { voiceService },
                    onSaveApiKey = { key ->
                        lifecycleScope.launch {
                            dataStore.edit { it[API_KEY] = key }
                            voiceService?.setApiKey(key)
                        }
                    },
                    getApiKey = {
                        var key = ""
                        lifecycleScope.launch {
                            key = dataStore.data.map { it[API_KEY] ?: "" }.first()
                        }
                        key
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindVoiceService()
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            startVoiceService()
        }
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindVoiceService()
    }

    private fun bindVoiceService() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    getService: () -> VoiceAssistantService?,
    onSaveApiKey: (String) -> Unit,
    getApiKey: () -> String
) {
    var showSettings by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }

    val service = getService()

    val assistantState by service?.assistantState?.collectAsState()
        ?: remember { mutableStateOf(VoiceAssistantService.AssistantState.Idle) }
    val lastResponse by service?.lastResponse?.collectAsState()
        ?: remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apiKey = getApiKey()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claude Ray-Bans") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Instellingen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status indicator
                StatusCard(assistantState)

                Spacer(modifier = Modifier.height(32.dp))

                // Main action button
                MicrophoneButton(
                    state = assistantState,
                    onClick = { service?.toggleListening() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Instructions
                Text(
                    text = when (assistantState) {
                        is VoiceAssistantService.AssistantState.Idle ->
                            "Tik op je Ray-Bans of druk op de knop om te praten"
                        is VoiceAssistantService.AssistantState.Listening ->
                            "Luisteren... Zeg iets tegen Claude"
                        is VoiceAssistantService.AssistantState.Processing ->
                            "Even denken..."
                        is VoiceAssistantService.AssistantState.Speaking ->
                            "Claude spreekt..."
                        is VoiceAssistantService.AssistantState.Error ->
                            "Er is iets misgegaan"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Last response
                if (lastResponse.isNotBlank()) {
                    ResponseCard(lastResponse)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Quick actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickActionButton(
                        text = "Herhaal",
                        onClick = { service?.repeatLastResponse() }
                    )
                    QuickActionButton(
                        text = "Nieuw gesprek",
                        onClick = { service?.clearConversation() }
                    )
                }
            }
        }
    }

    // Settings dialog
    if (showSettings) {
        SettingsDialog(
            apiKey = apiKey,
            onApiKeyChange = { apiKey = it },
            onSave = {
                onSaveApiKey(apiKey)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun StatusCard(state: VoiceAssistantService.AssistantState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is VoiceAssistantService.AssistantState.Listening ->
                    MaterialTheme.colorScheme.primaryContainer
                is VoiceAssistantService.AssistantState.Processing ->
                    MaterialTheme.colorScheme.secondaryContainer
                is VoiceAssistantService.AssistantState.Speaking ->
                    MaterialTheme.colorScheme.tertiaryContainer
                is VoiceAssistantService.AssistantState.Error ->
                    MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (state) {
                    is VoiceAssistantService.AssistantState.Listening -> Icons.Default.Mic
                    else -> Icons.Default.MicOff
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = when (state) {
                    is VoiceAssistantService.AssistantState.Idle -> "Klaar om te luisteren"
                    is VoiceAssistantService.AssistantState.Listening -> "Luisteren..."
                    is VoiceAssistantService.AssistantState.Processing -> "Verwerken..."
                    is VoiceAssistantService.AssistantState.Speaking -> "Spreken..."
                    is VoiceAssistantService.AssistantState.Error -> "Fout: ${state.message}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun MicrophoneButton(
    state: VoiceAssistantService.AssistantState,
    onClick: () -> Unit
) {
    val isActive = state is VoiceAssistantService.AssistantState.Listening ||
                   state is VoiceAssistantService.AssistantState.Processing

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (state is VoiceAssistantService.AssistantState.Listening) 1.1f else 1f,
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .background(
                color = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (state is VoiceAssistantService.AssistantState.Listening)
                Icons.Default.Mic
            else
                Icons.Default.MicOff,
            contentDescription = "Microfoon",
            modifier = Modifier.size(48.dp),
            tint = if (isActive)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun ResponseCard(response: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Claude zegt:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = response,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun QuickActionButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick) {
        Text(text)
    }
}

@Composable
fun SettingsDialog(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Instellingen") },
        text = {
            Column {
                Text(
                    text = "Voer je Anthropic API key in om Claude te gebruiken.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    placeholder = { Text("sk-ant-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Haal je API key op bij console.anthropic.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Opslaan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuleren")
            }
        }
    )
}
