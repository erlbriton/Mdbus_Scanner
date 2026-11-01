package com.intmash.modbus_scanner.data

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

data class PageInfo(
    val id: String,          // например, "p1000"
    val title: String,       // например, "Основные данные"
    val parameters: List<String> // список параметров страницы
)

class IniParser {
    fun parsePages(context: Context, uri: Uri): List<PageInfo> {
        val pages = mutableListOf<PageInfo>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, java.nio.charset.Charset.forName("Windows-1251"))).use { reader ->
                    var currentSection = ""
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                            currentSection = trimmed.substring(1, trimmed.length - 1)
                        } else if (currentSection.equals("PAGES", ignoreCase = true) && trimmed.contains("=")) {
                            val parts = trimmed.split("=", limit = 2)
                            if (parts.size == 2) {
                                val pageId = parts[0].trim()
                                val valuePart = parts[1].trim()

                                // Формат: Название/параметр1,параметр2,.../
                                val subParts = valuePart.split("/", limit = 3)
                                val title = if (subParts.isNotEmpty() && subParts[0].isNotBlank()) {
                                    subParts[0].trim()
                                } else {
                                    pageId
                                }

                                val rawParams = if (subParts.size > 1) subParts[1] else ""
                                val params = if (rawParams.isNotBlank()) {
                                    rawParams.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                } else {
                                    emptyList()
                                }

                                pages.add(PageInfo(pageId, title, params))
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return pages
    }
}