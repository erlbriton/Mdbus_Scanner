package com.intmash.modbus_scanner.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * FileUtils — утилитный объект (Singleton) для вспомогательных операций с файлами.
 *
 * В Kotlin ключевое слово `object` автоматически создает класс, у которого существует
 * только один глобальный экземпляр (синглтон). Это идеальный выбор для создания
 * «чистых» функций-помощников (helpers), которым не нужно сохранять внутреннее состояние.
 */
object FileUtils {

    /**
     * Извлекает реальное имя файла по его универсальному идентификатору (Uri),
     * который возвращается системным диалогом выбора файлов (Storage Access Framework).
     *
     * @param context Контекст приложения, необходимый для обращения к системным службам.
     * @param uri Универсальный указатель на выбранный файл (например, content://media/... или file://...).
     * @return Возвращает строку с именем файла или null, если имя извлечь не удалось.
     */
    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null

        // 1. Основной путь: если схема URI начинается с "content" (современный стандарт Android для файлов)
        if (uri.scheme == "content") {
            // ContentResolver — это специальный компонент Android, который позволяет приложениям
            // обмениваться данными через стандартизированный интерфейс (ContentProvider).
            // Мы запрашиваем у системы метаданные выбранного файла.
            val cursor = context.contentResolver.query(uri, null, null, null, null)

            try {
                // Проверяем, что запрос вернул данные и курсор содержит хотя бы одну строку
                if (cursor != null && cursor.moveToFirst()) {
                    // Ищем индекс колонки, содержащей системное имя файла (DISPLAY_NAME)
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        // Извлекаем строку с именем файла из найденной колонки
                        result = cursor.getString(index)
                    }
                }
            } finally {
                // ⚠️ ВАЖНО: Курсоры в Android удерживают системные ресурсы.
                // Блок `finally` гарантирует, что курсор будет закрыт даже в случае ошибки,
                // что защищает приложение от утечек памяти (Memory Leaks).
                cursor?.close()
            }
        }

        // 2. Запасной вариант (Fallback): если по какой-то причине через ContentResolver имя не получено
        // (например, старый тип ссылок или прямой путь файловой системы).
        if (result == null) {
            result = uri.path
            // Ищем последний символ косой черты ('/') в строке пути
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                // Обрезаем путь так, чтобы осталось только то, что идет после последнего слэша (имя файла)
                result = result.substring(cut + 1)
            }
        }

        return result
    }
}