package com.intmash.modbus_scanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intmash.modbus_scanner.data.PageInfo

/**
 * ConfigScreen — экран конфигурации и навигации по страницам параметров.
 *
 * Отображается после того, как пользователь успешно выбрал INI-файл.
 * Этот компонент является **Stateless (без сохранения собственного состояния)**: он не хранит данные внутри себя,
 * а лишь отображает то, что передали параметры, и сообщает о действиях пользователя через колбэки (лямбды).
 *
 * @param deviceId Текущий ID контроллера или статус соединения.
 * @param selectedFileName Имя выбранного INI-файла (сейчас доступно в логике приложения).
 * @param pages Список страниц, распарсенных из секции [PAGES] INI-файла.
 * @param selectedPage Страница, которая в данный момент выбрана пользователем.
 * @param onRefreshIdClick Колбэк, вызываемый при нажатии на кнопку "ID" для обновления данных.
 * @param onPageSelected Колбэк, вызываемый при выборе конкретной страницы в списке меню.
 * @param modifier Модификатор компоновки.
 */
@Composable
fun ConfigScreen(
    deviceId: String,
    selectedFileName: String,
    pages: List<PageInfo>,
    selectedPage: PageInfo?,
    onRefreshIdClick: () -> Unit,
    onPageSelected: (PageInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    // Корневой вертикальный контейнер экрана со стандартными отступами
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- ВЕРХНЯЯ ПАНЕЛЬ (CARD) ---
        // Карточка, которая держит кнопку принудительного опроса "ID" и статус контроллера на виду
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant // Цвет фона из палитры Material 3
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Кнопка принудительного обновления ID
                Button(onClick = onRefreshIdClick) {
                    Text("ID", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Текстовый блок с информацией о контроллере (выравнивание по правому краю)
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Контроллер ID:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = deviceId,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.height(16.dp))

        // --- ВЕРТИКАЛЬНОЕ МЕНЮ СТРАНИЦ НА ВЕСЬ ЭКРАН ---
        // LazyColumn — эффективный аналог RecyclerView из классического Android.
        // Он создает элементы списка "на лету" по мере их появления на экране (ленивая загрузка),
        // что критично для экономии памяти при больших списках.
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp), // Отступы между кнопками страниц
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Занимает всё оставшееся вертикальное пространство экрана
        ) {
            // items строит элементы списка на основе переданного листа pages
            items(pages) { page ->
                Button(
                    onClick = { onPageSelected(page) }, // Передаем выбранную страницу в колбэк
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2), // Фиксированный цвет фона кнопки (насыщенный синий)
                        contentColor = Color.White            // Цвет текста внутри кнопки
                    ),
                    shape = RoundedCornerShape(8.dp),       // Скругление углов кнопки радиусом 8 dp
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)                       // Фиксированная удобная для нажатия высота кнопки
                ) {
                    Text(
                        text = page.title,                  // Название страницы из INI-файла
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}