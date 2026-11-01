package com.intmash.modbus_scanner

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.intmash.modbus_scanner.ui.ConfigScreen
import com.intmash.modbus_scanner.ui.MainScreen
import com.intmash.modbus_scanner.ui.MainViewModel
import com.intmash.modbus_scanner.ui.SplashScreen
import com.intmash.modbus_scanner.ui.theme.ModbusIDTheme
import kotlinx.coroutines.delay
import java.io.File

/**
 * MainActivity — главная точка входа приложения Android.
 * Наследуется от ComponentActivity, что дает полную поддержку Jetpack Compose и Lifecycle-aware компонентов.
 */
class MainActivity : ComponentActivity() {

    // Делегат `by viewModels()` связывает ViewModel с жизненным циклом Activity.
    private val viewModel: MainViewModel by viewModels()

    // Системный сервис Android для работы с подключенными по USB устройствами (USB Host Mode)
    private lateinit var usbManager: UsbManager

    /**
     * BroadcastReceiver — приемник системных широковещательных сообщений.
     * Отвечает за обработку ответа от пользователя на запрос прав доступа к USB.
     */
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

        // Включает отображение контента "от края до края" (под строкой состояния и навигационной панелью)
        enableEdgeToEdge()

        // Получаем доступ к системной службе управления USB
        usbManager = getSystemService(USB_SERVICE) as UsbManager

        // Сканируем USB-порт при старте приложения
        checkUsbDevices()

        // setContent определяет UI приложения с помощью декларативного фреймворка Jetpack Compose
        setContent {
            // Применяем общую тему оформления (Material3)
            ModbusIDTheme {

                var showSplash by remember { mutableStateOf(true) }
                var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
                var selectedFileName by remember { mutableStateOf<String?>(null) }
                var errorMessage by remember { mutableStateOf<String?>(null) }

                // Состояния для собственного встроенного файлового браузера
                val context = this
                var showFileBrowser by remember { mutableStateOf(false) }
                var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
                var fileList by remember { mutableStateOf<List<File>>(emptyList()) }

                // Функция обновления списка файлов и папок в текущей директории
                fun refreshFiles(dir: File) {
                    try {
                        if (dir.exists() && dir.isDirectory) {
                            fileList = dir.listFiles()?.sortedWith(
                                compareBy({ !it.isDirectory }, { it.name.lowercase() })
                            ) ?: emptyList()
                        }
                    } catch (e: Exception) {
                        Log.e("FileBrowser", "Ошибка чтения папки: ${e.message}")
                    }
                }

                // LaunchedEffect выполняется один раз при старте Composable-функции в контексте корутины.
                // Задает задержку в 3 секунды для показа стартового сплеш-скрина.
                LaunchedEffect(Unit) {
                    delay(3000)
                    showSplash = false
                }

                // Scaffold — базовая каркасная структура Material Design, обеспечивающая правильные отступы (innerPadding)
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when {
                        // 1. Показываем заставку при запуске
                        showSplash -> {
                            SplashScreen(modifier = Modifier.padding(innerPadding))
                        }
                        // 2. Экран собственного файлового браузера (выбор любой папки и файла)
                        showFileBrowser -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Путь: ${currentDir.absolutePath}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Кнопка возврата в родительскую папку
                                if (currentDir.parentFile != null) {
                                    Button(
                                        onClick = {
                                            currentDir.parentFile?.let {
                                                currentDir = it
                                                refreshFiles(it)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("📁 .. (Наверх)")
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Список папок и файлов
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(fileList.size) { index ->
                                        val file = fileList[index]
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (file.isDirectory) {
                                                        currentDir = file
                                                        refreshFiles(file)
                                                    } else if (file.name.endsWith(".ini", ignoreCase = true)) {
                                                        val uri = Uri.fromFile(file)
                                                        selectedFileUri = uri
                                                        selectedFileName = file.name
                                                        errorMessage = null
                                                        showFileBrowser = false
                                                        viewModel.loadIniFile(context, uri)
                                                    }
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (file.isDirectory)
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                else
                                                    MaterialTheme.colorScheme.primaryContainer
                                            )
                                        ) {
                                            Text(
                                                text = if (file.isDirectory) "📁 ${file.name}" else "📄 ${file.name}",
                                                modifier = Modifier.padding(12.dp),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showFileBrowser = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Закрыть проводник")
                                }
                            }
                        }
                        // 3. Главный экран (до выбора файла)
                        selectedFileName == null -> {
                            MainScreen(
                                deviceId = viewModel.deviceId,
                                isConnected = viewModel.isConnected,
                                isVirtualDevice = IS_VIRTUAL_DEVICE,
                                onSelectFileClick = {
                                    // На Android 11+ запрашиваем разрешение на полный доступ к файлам
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                        !Environment.isExternalStorageManager()) {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = Intent(Settings.ACTION_SETTINGS)
                                            context.startActivity(intent)
                                        }
                                    } else {
                                        currentDir = Environment.getExternalStorageDirectory()
                                        refreshFiles(currentDir)
                                        showFileBrowser = true
                                    }
                                },
                                errorMessage = errorMessage,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        // 4. После успешного выбора INI-файла переходим на экран меню и таблицы параметров
                        else -> {
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

    /**
     * Проверяет список подключенных USB-устройств.
     */
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

    /**
     * Очистка ресурсов при уничтожении Activity.
     */
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