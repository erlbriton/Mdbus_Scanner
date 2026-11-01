package com.intmash.modbus_scanner.ui

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intmash.modbus_scanner.data.ModbusRepository
import com.intmash.modbus_scanner.data.PageInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MainViewModel — класс модели представления (ViewModel) в архитектуре MVVM.
 *
 * Главные задачи ViewModel:
 * 1. Хранение и управление состоянием интерфейса (UI State), которое переживает
 *    переворот экрана (в отличие от обычной Activity, которая уничтожается и создается заново).
 * 2. Инкапсуляция бизнес-логики: вызовы репозитория, запуск фоновых задач (корутин),
 *    парсинг файлов — всё это скрыто от UI, делая экраны «чистыми».
 */
class MainViewModel : ViewModel() {

    // Экземпляр репозитория для работы с железом и Modbus
    private val repository = ModbusRepository()

    // --- СОСТОЯНИЯ (UI States) ---
    // Использование делегатов `by mutableStateOf(...)` автоматически уведомляет Jetpack Compose
    // о необходимости обновить (перерисовать) экран при изменении этих переменных.

    // Текущий ID контроллера или статус соединения
    var deviceId by mutableStateOf("Ожидание ответа...")
        private set // private set запрещает менять это состояние снаружи (только изнутри ViewModel)

    // Флаг успешного физического подключения
    var isConnected by mutableStateOf(false)
        private set

    // Список страниц, распарсенных из INI-файла [PAGES]
    var pages by mutableStateOf<List<PageInfo>>(emptyList())
        private set

    // Выбранная пользователем страница из меню
    var selectedPage by mutableStateOf<PageInfo?>(null)
        private set

    // Ссылка на активную фоновую задачу (корутину) опроса устройства
    private var pollingJob: Job? = null

    /**
     * Инициализирует и открывает USB-порт, а при успехе запускает фоновый опрос устройства.
     */
    fun initializeAndConnect(usbManager: UsbManager, device: UsbDevice) {
        val success = repository.openPort(usbManager, device)
        if (success) {
            isConnected = true
            startPolling() // Запускаем фоновое чтение ID
        } else {
            deviceId = "Ошибка подключения к порту"
        }
    }

    /**
     * Принудительно обновляет ID устройства (перезапускает процесс опроса).
     */
    fun refreshDeviceId() {
        deviceId = "Обновление ID..."
        startPolling()
    }

    /**
     * Загружает и парсит выбранный пользователем INI-файл через IniParser.
     */
    fun loadIniFile(context: android.content.Context, uri: Uri) {
        val parser = com.intmash.modbus_scanner.data.IniParser()
        pages = parser.parsePages(context, uri)

        // Если страницы успешно найдены, автоматически выбираем первую страницу по умолчанию
        if (pages.isNotEmpty()) {
            selectedPage = pages.first()
        }
    }

    /**
     * Обрабатывает выбор конкретной страницы пользователем в меню.
     */
    fun selectPage(page: PageInfo) {
        selectedPage = page
    }

    /**
     * Запускает фоновый процесс опроса контроллера в корутине (потоке без блокировки UI).
     */
    private fun startPolling() {
        // Если предыдущий цикл опроса уже шел — отменяем его, чтобы не плодить дубликаты
        pollingJob?.cancel()

        // viewModelScope — специальная область видимости корутин ViewModel.
        // Когда ViewModel уничтожается, все запущенные в ней корутины автоматически отменяются (защита от утечек).
        pollingJob = viewModelScope.launch {
            val maxAttempts = 5
            for (attempt in 1..maxAttempts) {
                if (!isActive) break

                // Запрашиваем Device ID из репозитория (низкоуровневый Modbus-запрос)
                val result = repository.readDeviceId()
                if (result != null) {
                    deviceId = result
                    break // Если ID успешно получен — выходим из цикла
                }

                if (attempt == maxAttempts) {
                    deviceId = "Не удалось прочитать ID"
                } else {
                    // Если ответ не пришел, ждем 1 секунду перед следующей попыткой
                    delay(1000)
                }
            }
        }
    }

    /**
     * Метод жизненного цикла ViewModel.
     * Вызывается автоматически, когда экран окончательно закрывается и ViewModel больше не нужна.
     */
    override fun onCleared() {
        super.onCleared()
        // Обязательно отменяем фоновые задачи и закрываем физический USB-порт
        pollingJob?.cancel()
        repository.close()
    }
}