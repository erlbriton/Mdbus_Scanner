package com.intmash.modbus_scanner

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.intmash.modbus_scanner.ui.ConfigScreen
import com.intmash.modbus_scanner.ui.MainScreen
import com.intmash.modbus_scanner.ui.MainViewModel
import com.intmash.modbus_scanner.ui.SplashScreen
import com.intmash.modbus_scanner.ui.theme.ModbusIDTheme
import com.intmash.modbus_scanner.utils.FileUtils
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var usbManager: UsbManager

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("DEBUG", "onReceive вызван с action: ${intent.action}")
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { viewModel.initializeAndConnect(usbManager, it) }
                    } else {
                        Log.e("USB", "Доступ к устройству отклонен пользователем")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        usbManager = getSystemService(USB_SERVICE) as UsbManager

        checkUsbDevices()

        setContent {
            ModbusIDTheme {
                var showSplash by remember { mutableStateOf(true) }
                var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
                var selectedFileName by remember { mutableStateOf<String?>(null) }
                var errorMessage by remember { mutableStateOf<String?>(null) }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val name = FileUtils.getFileName(this, uri) ?: "unknown.ini"
                        if (name.endsWith(".ini", ignoreCase = true)) {
                            selectedFileUri = uri
                            selectedFileName = name
                            errorMessage = null
                            viewModel.loadIniFile(this, uri)
                        } else {
                            errorMessage = "Ошибка: выбранный файл не является .ini файлом"
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    delay(3000)
                    showSplash = false
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (showSplash) {
                        SplashScreen(modifier = Modifier.padding(innerPadding))
                    } else {
                        if (selectedFileName == null) {
                            MainScreen(
                                deviceId = viewModel.deviceId,
                                isConnected = viewModel.isConnected,
                                isVirtualDevice = IS_VIRTUAL_DEVICE,
                                onSelectFileClick = {
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                },
                                errorMessage = errorMessage,
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            ConfigScreen(
                                deviceId = viewModel.deviceId,
                                selectedFileName = selectedFileName!!,
                                pages = viewModel.pages,
                                selectedPage = viewModel.selectedPage,
                                onRefreshIdClick = {
                                    viewModel.refreshDeviceId()
                                },
                                onPageSelected = { page ->
                                    viewModel.selectPage(page)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkUsbDevices() {
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            Log.e("USB", "Нет доступных USB-устройств")
        } else {
            for (device in deviceList.values) {
                Log.d("USB", "Найдено устройство: ${device.deviceName}, VendorId: ${device.vendorId}, ProductId: ${device.productId}")
                if (usbManager.hasPermission(device)) {
                    viewModel.initializeAndConnect(usbManager, device)
                } else {
                    val permissionIntent = PendingIntent.getBroadcast(
                        this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
                    )
                    usbManager.requestPermission(device, permissionIntent)
                }
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e("USB", "Ошибка при отписке ресивера: ${e.message}")
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.intmash.modbus_scanner.USB_PERMISSION"
        private const val IS_VIRTUAL_DEVICE = true
    }
}