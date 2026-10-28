package com.intmash.modbus_scanner

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intmash.modbus_scanner.ui.MainViewModel
import com.intmash.modbus_scanner.ui.SplashScreen
import com.intmash.modbus_scanner.ui.theme.ModbusIDTheme
import kotlinx.coroutines.delay
import androidx.core.content.IntentCompat
import androidx.core.content.ContextCompat

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
                // Состояние для управления показом заставки (например, покажем её на 2 секунды при старте)
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(2000) // Время показа заставки (2 секунды)
                    showSplash = false
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (showSplash) {
                        SplashScreen(modifier = Modifier.padding(innerPadding))
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.deviceId,
                                fontSize = 25.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
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
    }
}