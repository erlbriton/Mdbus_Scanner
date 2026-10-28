package com.intmash.modbus_scanner.ui

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intmash.modbus_scanner.data.ModbusRepository
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

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val result = repository.readDeviceId()
                if (result != null) {
                    deviceId = result
                    // Если успешно получили ID, можем остановить цикл опроса или продолжить
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