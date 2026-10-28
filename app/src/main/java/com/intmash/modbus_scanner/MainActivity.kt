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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intmash.modbus_scanner.ui.theme.ModbusIDTheme
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private var deviceId by mutableStateOf("Ожидание ответа...")
    private lateinit var usbManager: UsbManager
    private var usbPort: UsbSerialPort? = null
    private var requestJob: Job? = null
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("DEBUG", "onReceive вызван с action: ${intent.action}")
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { openUsbPort(it) }
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
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        checkUsbDevices()
        setContent {
            ModbusIDTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = deviceId,
                            fontSize = 25.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
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
                    Log.d("DEBUG", "Разрешение на USB уже есть, открываем порт")
                    openUsbPort(device)
                } else {
                    Log.d("DEBUG", "Разрешения на USB нет, запрашиваем")
                    val permissionIntent = PendingIntent.getBroadcast(
                        this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
                    )
                    usbManager.requestPermission(device, permissionIntent)
                }
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    private fun openUsbPort(device: UsbDevice) {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver: UsbSerialDriver? = availableDrivers.find { it.device == device }
        if (driver == null) {
            Log.e("USB", "Драйвер USB-устройства не найден")
            return
        }
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            Log.e("USB", "Не удалось открыть соединение с устройством")
            return
        }
        try {
            usbPort = driver.ports[0]
            usbPort?.apply {
                open(connection)
                dtr = false
                rts = false
                Thread.sleep(100)
                dtr = true
                rts = true
                setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                Log.d("USB", "USB-порт успешно открыт и настроен на 115200 8N1")
                startRequestLoop()
            }
        } catch (e: Exception) {
            Log.e("USB", "Ошибка при открытии или настройке порта: ${e.message}")
        }
    }

    private fun startRequestLoop() {
        requestJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive) {
                    requestDeviceId()
                    delay(1000)
                    if (deviceId != "Ожидание ответа..." && deviceId.isNotEmpty()) {
                        cancel()
                    }
                }
            } catch (e: Exception) {
                Log.e("Modbus", "Ошибка в корутине: ${e.message}")
            }
        }
    }

    private fun requestDeviceId() {
        val request = byteArrayOf(
            0x01.toByte(),
            0x11.toByte(),
            0x00.toByte(), 0x00.toByte()
        )
        val crc = calculateCRC16(request)
        val fullRequest = request + crc
        usbPort?.write(fullRequest, 1000)
        Log.d("Modbus", "Запрос ID устройства отправлен: ${fullRequest.joinToString(" ") { "%02X".format(it) }}")
        val buffer = ByteArray(64)
        val bytesRead = usbPort?.read(buffer, 1000) ?: 0
        if (bytesRead > 2) {
            val receivedData = buffer.copyOfRange(2, bytesRead).toString(Charsets.US_ASCII).trim()
            if (receivedData.isNotEmpty()) {
                deviceId = receivedData
                Log.d("Modbus", "ID устройства получен: $deviceId")
            } else {
                Log.e("Modbus", "Получен пустой ответ, продолжаем запросы...")
            }
        } else {
            Log.e("Modbus", "Ответ пуст или не пришел, повторяем запрос...")
        }
    }

    private fun calculateCRC16(data: ByteArray): ByteArray {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            for (i in 0 until 8) {
                crc = if ((crc and 0x0001) != 0) {
                    (crc shr 1) xor 0xA001
                } else {
                    crc shr 1
                }
            }
        }
        return byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        if (usbPort?.isOpen == true) {
            usbPort?.close()
        }
        requestJob?.cancel()
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.intmash.modbus_scanner.USB_PERMISSION"
    }
}