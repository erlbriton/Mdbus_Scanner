package com.intmash.modbus_scanner.data

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class ModbusRepository {
    private var usbPort: UsbSerialPort? = null

    fun openPort(usbManager: UsbManager, device: UsbDevice): Boolean {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = availableDrivers.find { it.device == device }
        if (driver == null) {
            Log.e("ModbusRepository", "Драйвер USB-устройства не найден")
            return false
        }

        val connection: UsbDeviceConnection = usbManager.openDevice(driver.device) ?: run {
            Log.e("ModbusRepository", "Не удалось открыть соединение с устройством")
            return false
        }

        try {
            usbPort = driver.ports[0].apply {
                open(connection)
                dtr = false
                rts = false
                Thread.sleep(100)
                dtr = true
                rts = true
                setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            }
            Log.d("ModbusRepository", "USB-порт успешно открыт и настроен на 115200 8N1")
            return true
        } catch (e: Exception) {
            Log.e("ModbusRepository", "Ошибка при открытии или настройке порта: ${e.message}")
            return false
        }
    }

    fun readDeviceId(): String? {
        val port = usbPort ?: return null
        val request = byteArrayOf(
            0x01.toByte(),
            0x11.toByte(),
            0x00.toByte(), 0x00.toByte()
        )
        val crc = calculateCRC16(request)
        val fullRequest = request + crc

        try {
            port.write(fullRequest, 1000)
            Log.d("ModbusRepository", "Запрос отправлен: ${fullRequest.joinToString(" ") { "%02X".format(it) }}")

            val buffer = ByteArray(64)
            val bytesRead = port.read(buffer, 1000)
            if (bytesRead > 2) {
                val receivedData = buffer.copyOfRange(2, bytesRead).toString(Charsets.US_ASCII).trim()
                if (receivedData.isNotEmpty()) {
                    return receivedData
                }
            }
        } catch (e: Exception) {
            Log.e("ModbusRepository", "Ошибка обмена данными: ${e.message}")
        }
        return null
    }

    fun close() {
        try {
            if (usbPort?.isOpen == true) {
                usbPort?.close()
            }
        } catch (e: Exception) {
            Log.e("ModbusRepository", "Ошибка при закрытии порта: ${e.message}")
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
}