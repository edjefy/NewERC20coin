package com.raybans.claudeassistant.audio

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles Bluetooth audio connection with Meta Ray-Bans
 * Manages audio routing and device detection
 */
class BluetoothAudioHandler(private val context: Context) {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothHeadset: BluetoothHeadset? = null
    private var audioManager: AudioManager? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDeviceInfo?>(null)
    val connectedDevice: StateFlow<BluetoothDeviceInfo?> = _connectedDevice.asStateFlow()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class BluetoothDeviceInfo(
        val name: String,
        val address: String,
        val isRayBans: Boolean
    )

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { handleDeviceConnected(it) }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    _connectedDevice.value = null
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED)
                    when (state) {
                        BluetoothHeadset.STATE_CONNECTED -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            device?.let { handleDeviceConnected(it) }
                        }
                        BluetoothHeadset.STATE_DISCONNECTED -> {
                            _connectionState.value = ConnectionState.Disconnected
                            _connectedDevice.value = null
                        }
                    }
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
                    if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                        _connectionState.value = ConnectionState.Connected
                    }
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                checkConnectedDevices()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
            }
        }
    }

    /**
     * Initialize Bluetooth handling
     */
    fun initialize(): Boolean {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            _connectionState.value = ConnectionState.Error("Bluetooth niet beschikbaar")
            return false
        }

        // Register receivers
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        context.registerReceiver(bluetoothReceiver, filter)

        // Get headset profile proxy
        bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)

        // Check for already connected devices
        checkConnectedDevices()

        return true
    }

    /**
     * Check for already connected Bluetooth audio devices
     */
    private fun checkConnectedDevices() {
        // Check via AudioManager for connected devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices?.forEach { device ->
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    _connectionState.value = ConnectionState.Connected
                    _connectedDevice.value = BluetoothDeviceInfo(
                        name = device.productName?.toString() ?: "Bluetooth Device",
                        address = "",
                        isRayBans = isMetaRayBans(device.productName?.toString() ?: "")
                    )
                    return
                }
            }
        }

        // Check via BluetoothHeadset profile
        if (hasBluetoothPermission()) {
            bluetoothHeadset?.connectedDevices?.firstOrNull()?.let { device ->
                handleDeviceConnected(device)
            }
        }
    }

    private fun handleDeviceConnected(device: BluetoothDevice) {
        if (!hasBluetoothPermission()) {
            _connectionState.value = ConnectionState.Error("Bluetooth permissie vereist")
            return
        }

        try {
            val deviceName = device.name ?: "Unknown Device"
            _connectionState.value = ConnectionState.Connected
            _connectedDevice.value = BluetoothDeviceInfo(
                name = deviceName,
                address = device.address,
                isRayBans = isMetaRayBans(deviceName)
            )
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Bluetooth permissie geweigerd")
        }
    }

    /**
     * Check if device is Meta Ray-Bans based on name
     */
    private fun isMetaRayBans(deviceName: String): Boolean {
        val lowerName = deviceName.lowercase()
        return lowerName.contains("ray-ban") ||
               lowerName.contains("rayban") ||
               lowerName.contains("meta") ||
               lowerName.contains("stories")
    }

    /**
     * Start Bluetooth SCO for voice communication
     */
    fun startBluetoothSco() {
        if (_connectionState.value == ConnectionState.Connected) {
            audioManager?.apply {
                mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                startBluetoothSco()
                @Suppress("DEPRECATION")
                isBluetoothScoOn = true
            }
        }
    }

    /**
     * Stop Bluetooth SCO
     */
    fun stopBluetoothSco() {
        audioManager?.apply {
            @Suppress("DEPRECATION")
            stopBluetoothSco()
            @Suppress("DEPRECATION")
            isBluetoothScoOn = false
            mode = AudioManager.MODE_NORMAL
        }
    }

    /**
     * Check if Bluetooth audio is available
     */
    fun isBluetoothAudioAvailable(): Boolean {
        return _connectionState.value == ConnectionState.Connected
    }

    /**
     * Check for Bluetooth permission
     */
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Cleanup resources
     */
    fun destroy() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
        stopBluetoothSco()
    }
}
