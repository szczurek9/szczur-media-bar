package com.szczurek9.mediabar.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bezpieczny podzbiór Markdowna: nagłówki (#..######), listy punktowane i numerowane, akapity
 * z nowymi liniami, **pogrubienie**, *kursywa*, ~~przekreślenie~~. Bez HTML, linków i obrazków.
 * Ten sam plik jest w Szczur Reminder i Szczur Media Bar (zmieniony tylko pakiet) – zmieniaj oba naraz.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val number: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^\\s*[-*+]\\s+(.*)$")
private val NUMBERED = Regex("^\\s*(\\d{1,9})[.)]\\s+(.*)$")

fun parseMarkdownBlocks(source: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotEmpty()) {
            out += MdBlock.Paragraph(para.toString())
            para.clear()
        }
    }
    for (raw in source.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
        val line = raw.trimEnd()
        if (line.isBlank()) {
            flush()
            continue
        }
        val h = HEADING.find(line)
        val b = BULLET.find(line)
        val n = NUMBERED.find(line)
        when {
            h != null -> {
                flush()
                out += MdBlock.Heading(h.groupValues[1].length, h.groupValues[2].trim())
            }
            b != null -> {
                flush()
                out += MdBlock.Bullet(b.groupValues[1])
            }
            n != null -> {
                flush()
                out += MdBlock.Numbered(n.groupValues[1].toIntOrNull() ?: 1, n.groupValues[2])
            }
            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line.trim())
            }
        }
    }
    flush()
    return out
}

/** Znaczniki traktowane jako formatowanie tylko wtedy, gdy mają domknięcie; `\*` daje literalną gwiazdkę. */
fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    var italic = false
    var strike = false
    val run = StringBuilder()

    fun flushRun() {
        if (run.isEmpty()) return
        val style = SpanStyle(
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        )
        pushStyle(style)
        append(run.toString())
        pop()
        run.clear()
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' && i + 1 < text.length && text[i + 1] in "*~\\#-" -> {
                run.append(text[i + 1])
                i += 2
            }
            text.startsWith("**", i) && (bold || text.indexOf("**", i + 2) >= 0) -> {
                flushRun(); bold = !bold; i += 2
            }
            text.startsWith("~~", i) && (strike || text.indexOf("~~", i + 2) >= 0) -> {
                flushRun(); strike = !strike; i += 2
            }
            c == '*' && !text.startsWith("**", i) &&
                (italic || (i + 1 < text.length && !text[i + 1].isWhitespace() && text.indexOf('*', i + 1) >= 0)) -> {
                flushRun(); italic = !italic; i += 1
            }
            else -> {
                run.append(c)
                i += 1
            }
        }
    }
    flushRun()
}

/** Zwykły tekst bez znaczników – do podglądu w powiadomieniach systemowych. */
fun markdownToPlain(source: String): String =
    parseMarkdownBlocks(source).joinToString("\n") { block ->
        when (block) {
            is MdBlock.Heading -> inlineMarkdown(block.text).text
            is MdBlock.Bullet -> "• " + inlineMarkdown(block.text).text
            is MdBlock.Numbered -> "${block.number}. " + inlineMarkdown(block.text).text
            is MdBlock.Paragraph -> inlineMarkdown(block.text).text
        }
    }

@Composable
fun MarkdownText(
    source: String,
    color: Color,
    modifier: Modifier = Modifier,
    baseSize: Float = 14f,
) {
    val blocks = remember(source) { parseMarkdownBlocks(source) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> InlineText(
                    block.text, color, (baseSize + (4 - block.level.coerceAtMost(4)) * 2 + 2), FontWeight.Bold,
                )
                is MdBlock.Bullet -> Row {
                    Text("•", color = color, fontSize = baseSize.sp, modifier = Modifier.width(18.dp))
                    InlineText(block.text, color, baseSize)
                }
                is MdBlock.Numbered -> Row {
                    Text("${block.number}.", color = color, fontSize = baseSize.sp, modifier = Modifier.width(26.dp))
                    InlineText(block.text, color, baseSize)
                }
                is MdBlock.Paragraph -> InlineText(block.text, color, baseSize)
            }
        }
    }
}

@Composable
private fun InlineText(text: String, color: Color, size: Float, weight: FontWeight? = null) {
    val annotated = remember(text) { inlineMarkdown(text) }
    Text(annotated, color = color, fontSize = size.sp, fontWeight = weight)
}
