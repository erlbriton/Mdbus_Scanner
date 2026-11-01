package com.intmash.modbus_scanner.data

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * ModbusRepository — класс-репозиторий, отвечающий за всю низкоуровневую работу
 * с железом: обнаружение USB-устройств, открытие последовательного порта (UART),
 * настройку параметров обмена и отправку/прием запросов по протоколу Modbus RTU.
 *
 * В архитектуре MVVM репозиторий выступает единым источником данных и избавляет
 * ViewModel от необходимости знать детали работы с железом (USB, байты, CRC).
 */
class ModbusRepository {

    // Ссылка на активный USB-последовательный порт.
    // Использует стороннюю библиотеку usb-serial-for-android для работы с UART-чипами (CP210x, CH340, FTDI и др.).
    private var usbPort: UsbSerialPort? = null

    /**
     * Открывает и настраивает USB-порт для связи с контроллером.
     *
     * @param usbManager Системный менеджер USB Android.
     * @param device Обнаруженное физическое USB-устройство.
     * @return Возвращает true, если порт успешно открыт и настроен, иначе false.
     */
    fun openPort(usbManager: UsbManager, device: UsbDevice): Boolean {
        // 1. Ищем подходящий драйвер для подключенного USB-устройства с помощью стандартного пробера библиотеки
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = availableDrivers.find { it.device == device }

        if (driver == null) {
            Log.e("ModbusRepository", "Драйвер USB-устройства не найден")
            return false
        }

        // 2. Открываем физическое соединение с устройством через системный UsbManager
        val connection: UsbDeviceConnection = usbManager.openDevice(driver.device) ?: run {
            Log.e("ModbusRepository", "Не удалось открыть соединение с устройством")
            return false
        }

        try {
            // 3. Берем первый порт устройства (driver.ports[0]) и настраиваем его линии управления
            usbPort = driver.ports[0].apply {
                open(connection) // Открываем порт через созданное соединение

                // Аппаратные линии DTR и RTS переводим в false, делаем паузу,
                // а затем в true — это часто используется для сброса или инициализации внешних микроконтроллеров
                dtr = false
                rts = false
                Thread.sleep(100) // Небольшая задержка в миллисекундах
                dtr = true
                rts = true

                // Настраиваем параметры UART-соединения:
                // Скорость: 115200 бод, Биты данных: 8, Стоп-биты: 1, Проверка четкости (Parity): Отсутствует (None) -> 8N1
                setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            }

            Log.d("ModbusRepository", "USB-порт успешно открыт и настроен на 115200 8N1")
            return true

        } catch (e: Exception) {
            Log.e("ModbusRepository", "Ошибка при открытии или настройке порта: ${e.message}")
            return false
        }
    }

    /**
     * Отправляет запрос по протоколу Modbus RTU для чтения идентификатора устройства (Device ID)
     * и возвращает результат в виде строки.
     */
    fun readDeviceId(): String? {
        val port = usbPort ?: return null

        // Формируем тело Modbus-запроса (байтовый массив).
        // 0x01 — адрес устройства (Slave ID), 0x11 — код функции (Report Slave ID / чтение ID),
        // далее идут два нулевых байта параметров.
        val request = byteArrayOf(
            0x01.toByte(),
            0x11.toByte(),
            0x00.toByte(), 0x00.toByte()
        )

        // Вычисляем контрольную сумму CRC16 для сформированного запроса
        val crc = calculateCRC16(request)

        // Соединяем тело запроса и 2 байта контрольной суммы в единый пакет (Modbus RTU Frame)
        val fullRequest = request + crc

        try {
            // Отправляем пакет в порт с таймаутом 1000 мс (1 секунда)
            port.write(fullRequest, 1000)
            Log.d("ModbusRepository", "Запрос отправлен: ${fullRequest.joinToString(" ") { "%02X".format(it) }}")

            // Создаем буфер для приема ответа от контроллера
            val buffer = ByteArray(64)
            // Читаем данные из порта с таймаутом 1000 мс
            val bytesRead = port.read(buffer, 1000)

            // Если прочитано больше 2 байт (есть полезные данные помимо заголовка/ CRC)
            if (bytesRead > 2) {
                // Копируем полезную нагрузку (отбрасывая первые 2 байта заголовка)
                // и преобразуем байты в читаемую ASCII-строку с обрезкой пробелов
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

    /**
     * Корректно закрывает USB-порт при завершении работы или переподключении.
     */
    fun close() {
        try {
            if (usbPort?.isOpen == true) {
                usbPort?.close()
            }
        } catch (e: Exception) {
            Log.e("ModbusRepository", "Ошибка при закрытии порта: ${e.message}")
        }
    }

    /**
     * Вычисляет контрольную сумму CRC16 (циклический избыточный код)
     * по стандарту Modbus (полином 0xA001).
     *
     * Контрольная сумма обязана присутствовать в конце каждого Modbus RTU пакета,
     * чтобы принимающая сторона могла проверить целостность данных и отсутствие помех в линии.
     */
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
        // Возвращаем CRC разбитым на два байта в порядке little-endian (младший байт вперед)
        return byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }
}