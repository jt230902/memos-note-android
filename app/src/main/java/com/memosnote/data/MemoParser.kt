package com.memosnote.data

import java.util.Date

object MemoParser {

    fun parseMemos(content: String): List<Memo> {
        if (content.isBlank()) return emptyList()

        val memos = mutableListOf<Memo>()
        val parts = content.split("---")
        var i = 0

        while (i < parts.size) {
            val part = parts[i].trim()
            if (part.isEmpty()) {
                i++
                continue
            }

            val date = Memo.parseDate(part)
            if (date != null && i + 1 < parts.size) {
                val memoContent = parts[i + 1].trim()
                if (memoContent.isNotEmpty()) {
                    memos.add(
                        Memo(
                            id = Memo.generateId(),
                            content = memoContent,
                            createdAt = date
                        )
                    )
                }
                i += 2
            } else {
                if (part.isNotEmpty()) {
                    memos.add(
                        Memo(
                            id = Memo.generateId(),
                            content = part,
                            createdAt = Date()
                        )
                    )
                }
                i++
            }
        }

        if (memos.isEmpty() && content.trim().isNotEmpty()) {
            memos.add(
                Memo(
                    id = Memo.generateId(),
                    content = content.trim(),
                    createdAt = Date()
                )
            )
        }

        return memos
    }

    fun serializeMemos(memos: List<Memo>): String {
        return memos.joinToString("\n") { memo ->
            "---\n${Memo.formatDate(memo.createdAt)}\n---\n\n${memo.content}\n"
        }
    }
}
