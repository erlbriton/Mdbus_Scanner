package com.intmash.modbus_scanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MainScreen — главный экран приложения, который отображается до выбора INI-файла.
 *
 * Аннотация `@Composable` указывает компилятору, что эта функция является строительным блоком
 * пользовательского интерфейса в Jetpack Compose. Composable-функции описывают UI декларативно:
 * они не "изменяют" элементы напрямую при обновлении, а перерисовываются (recomposition) при изменении данных.
 *
 * @param deviceId Строка с текущим ID контроллера, полученным по Modbus.
 * @param isConnected Флаг успешности физического подключения к порту.
 * @param isVirtualDevice Флаг режима отладки/виртуального устройства (позволяет обойти проверку связи).
 * @param onSelectFileClick Лямбда-функция (колбэк), которая вызывается при нажатии на кнопку выбора файла.
 * @param errorMessage Текст ошибки (если она возникала при попытке выбрать файл), либо null.
 * @param modifier Модификатор для настройки внешнего вида, отступов и размеров контейнера.
 */
@Composable
fun MainScreen(
    deviceId: String,
    isConnected: Boolean,
    isVirtualDevice: Boolean,
    onSelectFileClick: () -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    // Box — это контейнер, который позволяет накладывать элементы друг на друга
    // или позиционировать их (в данном случае мы центрируем всё содержимое по центру экрана).
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Column — вертикальный контейнер, располагающий дочерние элементы друг под другом (как Linear Layout в вертикальном режиме).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, // Выравнивание дочерних элементов по центру по горизонтали
            verticalArrangement = Arrangement.Center,           // Выравнивание по центру по вертикали
            modifier = Modifier.padding(16.dp)                  // Внутренние отступы со всех сторон экрана
        ) {
            // Метка-подпись "Device ID:"
            Text(
                text = "Device ID:",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary // Цвет из текущей палитры темы Material 3
            )

            // Spacer — пустой элемент-отступ между текстами
            Spacer(modifier = Modifier.height(4.dp))

            // Отображение самого значения Device ID
            Text(
                text = deviceId,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Отступ перед разделительной полосой
            Spacer(modifier = Modifier.height(32.dp))

            // Горизонтальная линия-разделитель фиксированной ширины
            HorizontalDivider(modifier = Modifier.width(200.dp))

            // Отступ после разделительной полосы
            Spacer(modifier = Modifier.height(32.dp))

            // --- БИЗНЕС-ЛОГИКА И УСЛОВНЫЙ РЕНДЕРИНГ UI ---

            // Проверяем, получен ли реальный ID контроллера (исключая служебные тексты загрузки/ошибок)
            val isIdReceived = deviceId != "Ожидание ответа..." &&
                    deviceId != "Ошибка подключения к порту" &&
                    isConnected

            // Разрешаем выбор INI-файла либо если устройство виртуальное, либо если получена реальная связь
            val canSelectIni = isVirtualDevice || isIdReceived

            // Условный оператор в Kotlin позволяет динамически менять структуру интерфейса "на лету"
            if (canSelectIni) {
                // Кнопка выбора файла .ini
                Button(onClick = onSelectFileClick) {
                    Text("Выберите ini файл", fontSize = 16.sp)
                }

                // Если передана ошибка выбора файла — показываем её под кнопкой
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error, // Красный цвет ошибки из темы
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Если связи нет и устройство не виртуальное — блокируем выбор и выводим предупреждение
                Text(
                    text = "Ожидание ответа от контроллера...\nВыбор INI заблокирован",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}