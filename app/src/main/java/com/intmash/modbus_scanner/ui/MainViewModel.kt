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

class MainViewModel : ViewModel() {
    private val repository = ModbusRepository()

    // Состояния для пользовательского интерфейса
    var deviceId by mutableStateOf("Ожидание ответа...")
        private set

    var isConnected by mutableStateOf(false)
        private set

    // Новые состояния для страниц
    var pages by mutableStateOf<List<PageInfo>>(emptyList())
        private set

    var selectedPage by mutableStateOf<PageInfo?>(null)
        private set

    private var pollingJob: Job? = null

    fun initializeAndConnect(usbManager: UsbManager, device: UsbDevice) {
        val success = repository.openPort(usbManager, device)
        if (success) {
            isConnected = true
            startPolling()
        } else {
            deviceId = "Ошибка подключения к порту"
        }
    }

    fun refreshDeviceId() {
        deviceId = "Обновление ID..."
        startPolling()
    }

    fun loadIniFile(context: android.content.Context, uri: Uri) {
        val parser = com.intmash.modbus_scanner.data.IniParser()
        pages = parser.parsePages(context, uri)
        if (pages.isNotEmpty()) {
            selectedPage = pages.first()
        }
    }

    fun selectPage(page: PageInfo) {
        selectedPage = page
    }
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val result = repository.readDeviceId()
                if (result != null) {
                    deviceId = result
                    break
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        repository.close()
    }
}