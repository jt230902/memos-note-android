package com.memosnote.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Memo(
    val id: String,
    val content: String,
    val createdAt: Date,
    val updatedAt: Date? = null
) {
    companion object {
        private var counter = 0
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun generateId(): String {
            counter++
            return "memo-${System.currentTimeMillis()}-$counter"
        }

        fun formatDate(date: Date): String {
            return dateFormat.format(date)
        }

        fun parseDate(text: String): Date? {
            return try {
                dateFormat.parse(text.trim())
            } catch (e: Exception) {
                null
            }
        }
    }
}
