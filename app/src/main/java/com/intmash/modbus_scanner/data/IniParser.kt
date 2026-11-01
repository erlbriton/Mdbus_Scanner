package com.intmash.modbus_scanner.data

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * PageInfo — модель данных (Data Class), описывающая одну страницу параметров,
 * прочитанную из секции [PAGES] конфигурационного INI-файла.
 *
 * В Kotlin ключевое слово `data class` автоматически генерирует для класса полезные методы:
 * toString(), equals(), hashCode() и copy(), что избавляет от написания шаблонного кода.
 *
 * @property id Уникальный идентификатор страницы (например, "p1000").
 * @property title Человекочитаемое название страницы, отображаемое в меню (например, "Основные данные").
 * @property parameters Список параметров (тегов или адресов регистров), принадлежащих этой странице.
 */
data class PageInfo(
    val id: String,
    val title: String,
    val parameters: List<String>
)

/**
 * IniParser — класс, реализующий логику парсинга текстовых конфигурационных файлов формата INI.
 */
class IniParser {

    /**
     * Читает INI-файл по его URI и извлекает список страниц из секции [PAGES].
     *
     * @param context Контекст приложения для доступа к ContentResolver.
     * @param uri Ссылка на выбранный пользователем файл в памяти устройства.
     * @return Список объектов [PageInfo], найденных в файле. В случае ошибки возвращает пустой список.
     */
    fun parsePages(context: Context, uri: Uri): List<PageInfo> {
        val pages = mutableListOf<PageInfo>()

        try {
            // Открываем потоковое чтение файла через системный ContentResolver
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // ⚠️ ВАЖНО: Используем кодировку "Windows-1251", так как промышленные INI-файлы
                // часто содержат кириллицу в старой кодировке. Чтение через UTF-8 привело бы к "кракозябрам".
                BufferedReader(InputStreamReader(inputStream, java.nio.charset.Charset.forName("Windows-1251"))).use { reader ->
                    var currentSection = ""
                    var line: String? = reader.readLine()

                    // Построчно читаем файл до конца (пока строка не станет равной null)
                    while (line != null) {
                        val trimmed = line.trim() // Убираем лишние пробелы по краям строки

                        // 1. Проверяем, является ли строка заголовком секции (например, [PAGES] или [RAM])
                        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                            // Вырезаем имя секции без квадратных скобок (например, из "[PAGES]" получаем "PAGES")
                            currentSection = trimmed.substring(1, trimmed.length - 1)
                        }
                        // 2. Если мы находимся внутри секции [PAGES] и строка содержит знак равенства ('=')
                        else if (currentSection.equals("PAGES", ignoreCase = true) && trimmed.contains("=")) {
                            // Разделяем строку на две части по знаку '=' (максимум на 2 части)
                            val parts = trimmed.split("=", limit = 2)
                            if (parts.size == 2) {
                                val pageId = parts[0].trim()   // Ключ (например, p1000)
                                val valuePart = parts[1].trim() // Значение (например, "Основные данные/param1,param2/")

                                // Формат строки в INI: Название/параметр1,параметр2,.../
                                // Разбиваем значение на части по символу '/' (максимум на 3 части)
                                val subParts = valuePart.split("/", limit = 3)

                                // Извлекаем читаемое название (первая часть до слэша).
                                // Если название пустое, в качестве фоллбека используем pageId.
                                val title = if (subParts.isNotEmpty() && subParts[0].isNotBlank()) {
                                    subParts[0].trim()
                                } else {
                                    pageId
                                }

                                // Извлекаем сырую строку с параметрами (находится между первым и вторым слэшами)
                                val rawParams = if (subParts.size > 1) subParts[1] else ""

                                // Если строка с параметрами не пустая, разбиваем её по запятой ','
                                // и очищаем каждый элемент от пробелов
                                val params = if (rawParams.isNotBlank()) {
                                    rawParams.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                } else {
                                    emptyList()
                                }

                                // Создаем объект PageInfo и добавляем его в общий список страниц
                                pages.add(PageInfo(pageId, title, params))
                            }
                        }

                        // Читаем следующую строку
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            // Логируем или выводим стек ошибки, если файл поврежден или недоступен
            e.printStackTrace()
        }

        return pages
    }
}