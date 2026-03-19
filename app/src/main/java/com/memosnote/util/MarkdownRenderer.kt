package com.memosnote.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import kotlin.math.abs

object MarkdownRenderer {

    private val tagColors = listOf(
        Pair(Color(0xFFE8D5D0), Color(0xFF6D5A55)),
        Pair(Color(0xFFD5D8E0), Color(0xFF555A6D)),
        Pair(Color(0xFFD0E0D8), Color(0xFF556D65)),
        Pair(Color(0xFFE0D5E8), Color(0xFF6D556D)),
        Pair(Color(0xFFE0DDD5), Color(0xFF6D6A5A)),
        Pair(Color(0xFFD5E0E8), Color(0xFF55656D))
    )

    private val tagColorsDark = listOf(
        Pair(Color(0xFF4D3D3A), Color(0xFFD4B8B0)),
        Pair(Color(0xFF3A3D4D), Color(0xFFB0B8D4)),
        Pair(Color(0xFF3A4D45), Color(0xFFB0D4C0)),
        Pair(Color(0xFF4D3A4D), Color(0xFFD4B0D4)),
        Pair(Color(0xFF4D4A3A), Color(0xFFD4D0B0)),
        Pair(Color(0xFF3A454D), Color(0xFFB0C8D4))
    )

    fun getTagColorIndex(tag: String): Int {
        var hash = 0
        for (ch in tag) {
            hash = ch.code + ((hash shl 5) - hash)
        }
        return abs(hash) % 6
    }

    fun getTagColors(isDark: Boolean): List<Pair<Color, Color>> {
        return if (isDark) tagColorsDark else tagColors
    }

    fun extractTags(content: String): List<String> {
        val tagRegex = Regex("#([\\w\\u4e00-\\u9fff]+)")
        return tagRegex.findAll(content).map { it.groupValues[1] }.distinct().toList()
    }

    data class RenderedLine(
        val text: AnnotatedString,
        val isCodeBlock: Boolean = false,
        val isBlockquote: Boolean = false,
        val isListItem: Boolean = false,
        val isTodoChecked: Boolean? = null,
        val headingLevel: Int = 0 // 0=普通文本, 1-6=标题级别
    )

    fun renderMarkdown(content: String, isDark: Boolean): List<RenderedLine> {
        val lines = content.split("\n")
        val result = mutableListOf<RenderedLine>()
        var inCodeBlock = false
        val codeBlockContent = StringBuilder()

        // 标题正则：匹配 1-6 个 # 后面跟空格
        val headingRegex = Regex("^(#{1,6})\\s+(.+)$")

        for (line in lines) {
            if (line.trimStart().startsWith("```")) {
                if (inCodeBlock) {
                    result.add(
                        RenderedLine(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) {
                                    append(codeBlockContent.toString().trimEnd())
                                }
                            },
                            isCodeBlock = true
                        )
                    )
                    codeBlockContent.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                if (codeBlockContent.isNotEmpty()) codeBlockContent.append("\n")
                codeBlockContent.append(line)
                continue
            }

            // 检测标题
            val headingMatch = headingRegex.find(line.trimStart())
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2]
                result.add(
                    RenderedLine(
                        text = renderInlineMarkdown(text, isDark),
                        headingLevel = level
                    )
                )
                continue
            }

            when {
                line.trimStart().startsWith("- [x]") || line.trimStart().startsWith("- [X]") -> {
                    val text = line.trimStart().removePrefix("- [x]").removePrefix("- [X]").trim()
                    result.add(
                        RenderedLine(
                            text = renderInlineMarkdown(text, isDark),
                            isTodoChecked = true
                        )
                    )
                }
                line.trimStart().startsWith("- [ ]") -> {
                    val text = line.trimStart().removePrefix("- [ ]").trim()
                    result.add(
                        RenderedLine(
                            text = renderInlineMarkdown(text, isDark),
                            isTodoChecked = false
                        )
                    )
                }
                line.trimStart().startsWith("- ") -> {
                    val text = line.trimStart().removePrefix("- ")
                    result.add(
                        RenderedLine(
                            text = renderInlineMarkdown(text, isDark),
                            isListItem = true
                        )
                    )
                }
                line.trimStart().startsWith("> ") -> {
                    val text = line.trimStart().removePrefix("> ")
                    result.add(
                        RenderedLine(
                            text = renderInlineMarkdown(text, isDark),
                            isBlockquote = true
                        )
                    )
                }
                else -> {
                    result.add(RenderedLine(text = renderInlineMarkdown(line, isDark)))
                }
            }
        }

        if (inCodeBlock && codeBlockContent.isNotEmpty()) {
            result.add(
                RenderedLine(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) {
                            append(codeBlockContent.toString().trimEnd())
                        }
                    },
                    isCodeBlock = true
                )
            )
        }

        return result
    }

    private fun renderInlineMarkdown(text: String, isDark: Boolean): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            val len = text.length
            val colors = getTagColors(isDark)

            while (i < len) {
                when {
                    // Bold **text**
                    i + 1 < len && text[i] == '*' && text[i + 1] == '*' -> {
                        val end = text.indexOf("**", i + 2)
                        if (end != -1) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(text.substring(i + 2, end))
                            }
                            i = end + 2
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                    // Italic *text*
                    text[i] == '*' && (i == 0 || text[i - 1] != '*') -> {
                        val end = text.indexOf('*', i + 1)
                        if (end != -1 && (end + 1 >= len || text[end + 1] != '*')) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(text.substring(i + 1, end))
                            }
                            i = end + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                    // Inline code `text`
                    text[i] == '`' -> {
                        val end = text.indexOf('`', i + 1)
                        if (end != -1) {
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    background = if (isDark) Color(0xFF3A3835) else Color(0xFFF0EDE8)
                                )
                            ) {
                                append(" ${text.substring(i + 1, end)} ")
                            }
                            i = end + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                    // Tag #tagname (注意：不要把标题符号当成标签)
                    text[i] == '#' && (i == 0 || text[i - 1] == ' ') -> {
                        val tagRegex = Regex("^#([\\w\\u4e00-\\u9fff]+)")
                        val match = tagRegex.find(text.substring(i))
                        if (match != null) {
                            val tag = match.groupValues[1]
                            val colorIdx = getTagColorIndex(tag)
                            val (bg, fg) = colors[colorIdx]
                            pushStringAnnotation("tag", tag)
                            withStyle(SpanStyle(color = fg, background = bg, fontSize = 13.sp)) {
                                append(" #$tag ")
                            }
                            pop()
                            i += match.value.length
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                    // Link [text](url)
                    text[i] == '[' -> {
                        val closeBracket = text.indexOf(']', i + 1)
                        if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                            val closeParen = text.indexOf(')', closeBracket + 2)
                            if (closeParen != -1) {
                                val linkText = text.substring(i + 1, closeBracket)
                                val url = text.substring(closeBracket + 2, closeParen)
                                pushStringAnnotation("url", url)
                                withStyle(
                                    SpanStyle(
                                        color = if (isDark) Color(0xFF8BAACC) else Color(0xFF6B8AAC),
                                        textDecoration = TextDecoration.Underline
                                    )
                                ) {
                                    append(linkText)
                                }
                                pop()
                                i = closeParen + 1
                            } else {
                                append(text[i])
                                i++
                            }
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                    else -> {
                        append(text[i])
                        i++
                    }
                }
            }
        }
    }
}