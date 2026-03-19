package com.memosnote.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class MemoRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("memos_prefs", Context.MODE_PRIVATE)
    private var currentUri: Uri? = null

    init {
        // 从 SharedPreferences 恢复上次使用的文件
        val savedUri = prefs.getString("current_file_uri", null)
        if (savedUri != null) {
            try {
                currentUri = Uri.parse(savedUri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hasCurrentFile(): Boolean = currentUri != null

    fun setCurrentFile(uri: Uri) {
        currentUri = uri
    }

    fun loadMemos(): List<Memo> {
        val content = when {
            currentUri != null -> readFromUri(currentUri!!)
            else -> ""
        }
        return if (content.isNotBlank()) MemoParser.parseMemos(content) else emptyList()
    }

    fun saveMemos(memos: List<Memo>) {
        if (currentUri == null) return
        val content = MemoParser.serializeMemos(memos)
        writeToUri(currentUri!!, content)
    }

    private fun readFromUri(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun writeToUri(uri: Uri, content: String) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(content)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 获取文件显示名称
    fun getCurrentFileName(): String {
        return currentUri?.let { uri ->
            DocumentFile.fromSingleUri(context, uri)?.name ?: "未知文件"
        } ?: "未选择文件"
    }
}