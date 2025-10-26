package com.wllcom.quicomguide.ui.components

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.wllcom.quicomguide.data.parser.ParsedMaterial

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HighlightedWebView(
    text: String,
    supportZoom: Boolean,
    sharedWebView: WebView,
    fontSize: Int = 12,
    backgroundColor: Color = MaterialTheme.colorScheme.surface
) {
    val isDark = isSystemInDarkTheme()
    val html = remember(text, isDark) {
        buildHtml(text, backgroundColor, fontSize, isDark)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            (sharedWebView.parent as? ViewGroup)?.removeView(sharedWebView)
            sharedWebView.apply {
                settings.setSupportZoom(supportZoom)
                settings.builtInZoomControls = supportZoom
                settings.displayZoomControls = false
            }
        },
        update = { webView ->
            webView.visibility = View.INVISIBLE
            webView.alpha = 0f
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "utf-8",
                null
            )
        }
    )
}

@Composable
fun HighlightedWebView(
    materialTitle: String,
    parsedMaterial: ParsedMaterial,
    supportZoom: Boolean,
    sharedWebView: WebView,
    fontSize: Int = 12,
    backgroundColor: Color = MaterialTheme.colorScheme.surface
) {
    val isDark = isSystemInDarkTheme()
    val html = remember(parsedMaterial, isDark) {
        buildHtml(materialTitle, parsedMaterial, backgroundColor, fontSize, isDark)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            (sharedWebView.parent as? ViewGroup)?.removeView(sharedWebView)
            sharedWebView.apply {
                settings.setSupportZoom(supportZoom)
                settings.builtInZoomControls = supportZoom
                settings.displayZoomControls = false
            }
        },
        update = { webView ->
            webView.visibility = View.INVISIBLE
            webView.alpha = 0f
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "utf-8",
                null
            )
        }
    )
}

fun buildHtml(
    materialTitle: String,
    parsed: ParsedMaterial,
    bgColor: Color,
    fontSize: Int = 12,
    isDark: Boolean = false
): String {
    val sectionsHtml = parsed.sections.joinToString("") { section ->
        val title = escapeHtml(section.title)
        val elems = section.elements.joinToString("") { (_, content) ->
            val html = convertInlineTagsToHtml(content)
            "<div class=\"example\">$html</div>"
        }
        "<section><h3>$title</h3>$elems</section>"
    }

    return processingHtml(
        "<h2>${escapeHtml(materialTitle)}</h2>\n$sectionsHtml",
        bgColor,
        fontSize,
        isDark
    )
}

fun buildHtml(text: String, bgColor: Color, fontSize: Int = 12, isDark: Boolean = false): String {
    return processingHtml(convertInlineTagsToHtml(text), bgColor, fontSize, isDark)
}

private fun processingHtml(
    html: String,
    bgColor: Color,
    fontSize: Int = 12,
    isDark: Boolean = false
): String {
    val cssHref =
        if (isDark) "file:///android_asset/github-dark.min.css" else "file:///android_asset/github.min.css"
    val tableCss =
        if (isDark) "file:///android_asset/table-dark.css" else "file:///android_asset/table-light.css"
    val tableJs = "file:///android_asset/table.js"

    val r = (bgColor.red * 255).toInt()
    val g = (bgColor.green * 255).toInt()
    val b = (bgColor.blue * 255).toInt()
    val bodyBg = String.format("#%02X%02X%02X", r, g, b)
    val bodyColor = if (isDark) "#c9d1d9" else "#24292e"
    val preBg = if (isDark) "#161b22" else "#f6f8fa"
    val strFontSize = fontSize.toString() + "px"

    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <link rel="stylesheet" href="$cssHref">
          <link rel="stylesheet" href="$tableCss">
          <link rel="stylesheet" href="file:///android_asset/katex.min.css">
          <script src="file:///android_asset/highlight.min.js"></script>
          <script src="file:///android_asset/katex.min.js"></script>
          <script src="file:///android_asset/auto-render.min.js"></script>
          <script src="$tableJs"></script>
          <script>
            hljs.configure({ cssSelector: 'pre code, code.inline' });
            hljs.highlightAll();
            document.addEventListener("DOMContentLoaded", function() {
                renderMathInElement(document.body, {
                    delimiters: [
                        {left: "\\[", right: "\\]", display: true},
                        {left: "\\(", right: "\\)", display: false}
                    ]
                });
            });
          </script>
          <style>
            body { 
                font-family: -apple-system, Roboto, sans-serif; 
                margin: 12px; 
                color: $bodyColor; 
                background: $bodyBg; 
                font-size: $strFontSize;
            }
            pre { 
                border-radius: 8px; 
                padding: 12px; 
                overflow-x: auto; 
                background: $preBg; 
                border: 1px solid rgba(0,0,0,0.08); 
            }
            code.inline { 
                background: $preBg; 
                padding: 0 6px; 
                border-radius: 6px; 
                font-family: monospace; 
            }
            .example { 
                margin: 8px 0; 
            }
            .formula {
                display: block;
                margin: 8px 0;
            }
          </style>
        </head>
        <body>
          $html
        </body>
        </html>
    """.trimIndent()
}

private fun convertInlineTagsToHtml(text: String): String {
    var result = text

    val tableRegex = Regex(
        """<table>(.*?)</table>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    result = tableRegex.replace(result) { tableMatch ->
        val innerRaw = tableMatch.groups[1]?.value ?: ""
        val lines = innerRaw
            .split(Regex("\r?\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        fun splitByPipePreservingEscapes(line: String): List<String> {
            val tmp = line.replace("\\\\", "\\\\\\\\")
                .replace("\\|", "__ESC_PIPE__")
            val trimmed = tmp.trim().trimStart('|').trimEnd('|')
            val parts = trimmed.split("|").map { it.trim().replace("__ESC_PIPE__", "|") }
            return parts
        }

        fun splitByComma(line: String): List<String> =
            line.split(",").map { it.trim() }

        val hasPipe = lines.any { it.contains("|") }
        val hasComma = lines.any { it.contains(",") }

        if (lines.isEmpty()) {
            """<div class="chat-table-wrapper"><table class="chat-table"><tbody></tbody></table></div>"""
        } else {
            val rowsCells: List<List<String>> = when {
                hasPipe -> lines.map { splitByPipePreservingEscapes(it) }
                !hasPipe && hasComma -> lines.map { splitByComma(it) }
                else -> {
                    lines.map { listOf(it) }
                }
            }

            fun isSeparatorLine(rawLine: String): Boolean {
                val s = rawLine.trim().trimStart('|').trimEnd('|').replace(" ", "")
                return s.isNotEmpty() && s.all { it == '-' || it == ':' || it == '|' }
            }

            val useThead = if (lines.size >= 2 && hasPipe) {
                isSeparatorLine(lines[1])
            } else {
                hasPipe
            }

            val colCount = rowsCells.maxOfOrNull { it.size } ?: 0

            val normalized = rowsCells.map { row ->
                if (row.size < colCount) row + List(colCount - row.size) { "" } else row
            }

            val theadHtml = if (useThead) {
                val headerRow = normalized[0]
                val headerCells = headerRow.joinToString("") { rawCell ->
                    val processed = convertInlineTagsToHtml(rawCell)
                    "<th>$processed</th>"
                }
                "<thead><tr>$headerCells</tr></thead>"
            } else {
                ""
            }

            val bodyRows = if (useThead && lines.size >= 2 && isSeparatorLine(lines[1])) {
                normalized.drop(2)
            } else if (useThead) {
                normalized.drop(1)
            } else {
                normalized
            }

            val tbodyHtml = bodyRows.joinToString("") { row ->
                val cells = row.joinToString("") { rawCell ->
                    val processed = convertInlineTagsToHtml(rawCell)
                    "<td>$processed</td>"
                }
                "<tr>$cells</tr>"
            }

            "<div class=\"chat-table-wrapper\"><table class=\"chat-table\">$theadHtml<tbody>$tbodyHtml</tbody></table></div>"
        }
    }

    val codeRegex =
        """<code(?:\s+language="([^"]+)")?>(.*?)</code>""".toRegex(setOf(RegexOption.DOT_MATCHES_ALL))
    result = codeRegex.replace(result) { match ->
        val lang = match.groups[1]?.value?.takeIf { it.isNotBlank() } ?: ""
        val content = escapeHtml(match.groups[2]?.value ?: "").trim()
        if (lang.isNotBlank()) {
            "<pre><code class=\"language-$lang\">$content</code></pre>"
        } else {
            "<pre><code>$content</code></pre>" // autodetect
        }
    }

    val inlineCodeRegex =
        """<inline-code(?:\s+language="([^"]+)")?>(.*?)</inline-code>""".toRegex(setOf(RegexOption.DOT_MATCHES_ALL))
    result = inlineCodeRegex.replace(result) { match ->
        val lang = match.groups[1]?.value?.takeIf { it.isNotBlank() } ?: ""
        val content = escapeHtml(match.groups[2]?.value ?: "")
        if (lang.isNotBlank()) {
            "<code class=\"inline language-$lang\">$content</code>"
        } else {
            "<code class=\"inline\">$content</code>"
        }
    }

    val texRegex = """<tex>(.*?)</tex>""".toRegex(setOf(RegexOption.DOT_MATCHES_ALL))
    result = texRegex.replace(result) { match ->
        val texContent = match.groups[1]?.value?.trim() ?: ""
        """<span class="formula">\[${texContent}\]</span>"""
    }

    return result
}

private fun escapeHtml(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")