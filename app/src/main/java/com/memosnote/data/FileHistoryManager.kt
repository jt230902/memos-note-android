package com.memosnote.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecentFile(
    val uri: String,
    val name: String,
    val lastOpened: Long
) {
    fun getFormattedDate(): String {
        val date = Date(lastOpened)
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return format.format(date)
    }
}

class FileHistoryManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("file_history", Context.MODE_PRIVATE)
    private val KEY_RECENT_FILES = "recent_files"
    private val MAX_HISTORY_SIZE = 20

    fun addToHistory(uri: String, name: String) {
        val recentFiles = getRecentFiles().toMutableList()

        // 移除已存在的相同文件
        recentFiles.removeAll { it.uri == uri }

        // 添加到开头
        recentFiles.add(0, RecentFile(uri, name, System.currentTimeMillis()))

        // 限制数量
        val trimmedList = recentFiles.take(MAX_HISTORY_SIZE)

        // 保存
        saveRecentFiles(trimmedList)
    }

    fun getRecentFiles(): List<RecentFile> {
        val jsonString = prefs.getString(KEY_RECENT_FILES, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<RecentFile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RecentFile(
                        uri = obj.getString("uri"),
                        name = obj.getString("name"),
                        lastOpened = obj.getLong("lastOpened")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeFromHistory(uri: String) {
        val recentFiles = getRecentFiles().toMutableList()
        recentFiles.removeAll { it.uri == uri }
        saveRecentFiles(recentFiles)
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_RECENT_FILES).apply()
    }

    private fun saveRecentFiles(files: List<RecentFile>) {
        val jsonArray = JSONArray()
        files.forEach { file ->
            val obj = JSONObject()
            obj.put("uri", file.uri)
            obj.put("name", file.name)
            obj.put("lastOpened", file.lastOpened)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_RECENT_FILES, jsonArray.toString()).apply()
    }
}
